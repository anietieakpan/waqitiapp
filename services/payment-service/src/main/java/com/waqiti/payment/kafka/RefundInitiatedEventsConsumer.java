package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.payment.domain.Refund;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.RefundStatus;
import com.waqiti.payment.domain.RefundType;
import com.waqiti.payment.repository.RefundRepository;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.service.RefundProcessingService;
import com.waqiti.payment.service.RefundValidationService;
import com.waqiti.payment.service.PaymentGatewayService;
import com.waqiti.payment.service.NotificationService;
import com.waqiti.common.audit.AuditService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class RefundInitiatedEventsConsumer {

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final RefundProcessingService refundProcessingService;
    private final RefundValidationService refundValidationService;
    private final PaymentGatewayService gatewayService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private final Counter refundInitiatedCounter;
    private final Counter refundProcessedCounter;
    private final Counter refundFailedCounter;
    private final Timer refundProcessingTimer;
    
    private final Map<String, String> processedEvents = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_MS = TimeUnit.HOURS.toMillis(24);

    public RefundInitiatedEventsConsumer(
            RefundRepository refundRepository,
            PaymentRepository paymentRepository,
            RefundProcessingService refundProcessingService,
            RefundValidationService refundValidationService,
            PaymentGatewayService gatewayService,
            NotificationService notificationService,
            AuditService auditService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.refundProcessingService = refundProcessingService;
        this.refundValidationService = refundValidationService;
        this.gatewayService = gatewayService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        
        this.refundInitiatedCounter = Counter.builder("refund.initiated.events")
            .description("Count of refund initiated events")
            .register(meterRegistry);
        
        this.refundProcessedCounter = Counter.builder("refund.processed.events")
            .description("Count of successfully processed refunds")
            .register(meterRegistry);
        
        this.refundFailedCounter = Counter.builder("refund.failed.events")
            .description("Count of failed refund processing")
            .register(meterRegistry);
        
        this.refundProcessingTimer = Timer.builder("refund.processing.duration")
            .description("Time taken to process refund initiated events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = "refund-initiated-events",
        groupId = "payment-refund-initiated-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltTopic = "refund-initiated-events-dlt",
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void handleRefundInitiatedEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start();
        String eventId = String.format("%s-%d-%d-%d", topic, partition, offset, System.currentTimeMillis());
        
        log.info("Received refund initiated event - eventId: {}, partition: {}, offset: {}", 
            eventId, partition, offset);
        
        try {
            if (isAlreadyProcessed(eventId)) {
                log.info("Event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }
            
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            
            String paymentId = (String) eventData.get("paymentId");
            String refundId = (String) eventData.get("refundId");
            BigDecimal refundAmount = new BigDecimal(eventData.get("refundAmount").toString());
            String refundType = (String) eventData.get("refundType");
            String gatewayId = (String) eventData.get("gatewayId");
            String correlationId = (String) eventData.get("correlationId");
            
            log.info("Processing refund initiated: paymentId={}, refundId={}, amount={}, type={}, correlationId={}", 
                paymentId, refundId, refundAmount, refundType, correlationId);
            
            refundInitiatedCounter.increment();
            
            processRefundInitiated(paymentId, refundId, refundAmount, refundType, gatewayId, 
                eventData, correlationId);
            
            markAsProcessed(eventId);
            acknowledgment.acknowledge();

            refundProcessingTimer.stop(sample);

            log.info("Successfully processed refund initiated event: {}", eventId);
            
        } catch (Exception e) {
            log.error("Failed to process refund initiated event {}: {}", eventId, e.getMessage(), e);
            refundFailedCounter.increment();
            throw new RuntimeException("Refund initiated processing failed", e);
        }
    }

    @CircuitBreaker(name = "payment", fallbackMethod = "processRefundInitiatedFallback")
    @Retry(name = "payment")
    private void processRefundInitiated(
            String paymentId,
            String refundId,
            BigDecimal refundAmount,
            String refundType,
            String gatewayId,
            Map<String, Object> eventData,
            String correlationId) {
        
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));
        
        refundValidationService.validateRefundEligibility(payment, refundAmount, correlationId);
        
        Refund refund = Refund.builder()
            .id(refundId)
            .paymentId(paymentId)
            .amount(refundAmount)
            .refundType(RefundType.valueOf(refundType))
            .gatewayId(gatewayId)
            .status(RefundStatus.PENDING)
            .initiatedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        refundRepository.save(refund);
        
        try {
            String gatewayRefundId = gatewayService.initiateRefund(
                payment.getGatewayTransactionId(),
                refundAmount,
                gatewayId,
                correlationId
            );
            
            refund.setGatewayRefundId(gatewayRefundId);
            refund.setStatus(RefundStatus.PROCESSING);
            refundRepository.save(refund);
            
            payment.setRefundStatus("PROCESSING");
            payment.setRefundAmount(refundAmount);
            payment.setRefundInitiatedAt(LocalDateTime.now());
            paymentRepository.save(payment);
            
            kafkaTemplate.send("refund-processing-events", Map.of(
                "refundId", refundId,
                "paymentId", paymentId,
                "gatewayRefundId", gatewayRefundId,
                "amount", refundAmount,
                "status", "PROCESSING",
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
            
            notificationService.sendRefundInitiatedNotification(
                payment.getCustomerId(),
                refundId,
                refundAmount,
                correlationId
            );
            
            auditService.logPaymentEvent(
                "REFUND_INITIATED",
                paymentId,
                Map.of(
                    "refundId", refundId,
                    "refundAmount", refundAmount,
                    "refundType", refundType,
                    "gatewayRefundId", gatewayRefundId,
                    "correlationId", correlationId,
                    "timestamp", Instant.now().toString()
                )
            );
            
            refundProcessedCounter.increment();
            
            log.info("Refund initiated successfully: refundId={}, gatewayRefundId={}, correlationId={}", 
                refundId, gatewayRefundId, correlationId);
            
        } catch (Exception e) {
            log.error("Failed to initiate refund with gateway: refundId={}, error={}", refundId, e.getMessage(), e);
            
            refund.setStatus(RefundStatus.FAILED);
            refund.setFailureReason(e.getMessage());
            refund.setFailedAt(LocalDateTime.now());
            refundRepository.save(refund);
            
            payment.setRefundStatus("FAILED");
            payment.setRefundFailureReason(e.getMessage());
            paymentRepository.save(payment);
            
            kafkaTemplate.send("refund-failed-events", Map.of(
                "refundId", refundId,
                "paymentId", paymentId,
                "amount", refundAmount,
                "reason", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
            
            notificationService.sendRefundFailedNotification(
                payment.getCustomerId(),
                refundId,
                e.getMessage(),
                correlationId
            );
            
            throw e;
        }
    }

    private void processRefundInitiatedFallback(
            String paymentId,
            String refundId,
            BigDecimal refundAmount,
            String refundType,
            String gatewayId,
            Map<String, Object> eventData,
            String correlationId,
            Exception e) {
        
        log.error("Circuit breaker activated for refund initiated - refundId: {}, correlationId: {}, error: {}", 
            refundId, correlationId, e.getMessage());
        
        Map<String, Object> fallbackEvent = new HashMap<>();
        fallbackEvent.put("refundId", refundId);
        fallbackEvent.put("paymentId", paymentId);
        fallbackEvent.put("refundAmount", refundAmount);
        fallbackEvent.put("refundType", refundType);
        fallbackEvent.put("gatewayId", gatewayId);
        fallbackEvent.put("correlationId", correlationId);
        fallbackEvent.put("error", e.getMessage());
        fallbackEvent.put("fallbackTimestamp", Instant.now().toString());
        
        kafkaTemplate.send("refund-fallback-events", fallbackEvent);
    }

    @DltHandler
    public void handleDlt(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        log.error("Message sent to DLT - topic: {}, partition: {}, offset: {}, error: {}", 
            topic, partition, offset, exceptionMessage);
        
        try {
            Map<String, Object> dltEvent = Map.of(
                "originalTopic", topic,
                "partition", partition,
                "offset", offset,
                "message", message,
                "error", exceptionMessage,
                "timestamp", Instant.now().toString(),
                "dltReason", "MAX_RETRIES_EXCEEDED"
            );
            
            kafkaTemplate.send("refund-processing-failures", dltEvent);
            
            notificationService.sendOperationalAlert(
                "Refund Processing Failed",
                String.format("Failed to process refund after max retries. Error: %s", exceptionMessage),
                dltEvent
            );
            
        } catch (Exception e) {
            log.error("Failed to process DLT message: {}", e.getMessage(), e);
        }
    }

    private boolean isAlreadyProcessed(String eventId) {
        return processedEvents.containsKey(eventId);
    }

    private void markAsProcessed(String eventId) {
        processedEvents.put(eventId, String.valueOf(System.currentTimeMillis()));
        
        processedEvents.entrySet().removeIf(entry -> {
            long timestamp = Long.parseLong(entry.getValue());
            return System.currentTimeMillis() - timestamp > IDEMPOTENCY_TTL_MS;
        });
    }
}