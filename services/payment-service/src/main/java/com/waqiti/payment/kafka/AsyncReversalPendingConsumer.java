package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.payment.model.*;
import com.waqiti.payment.repository.AsyncReversalRepository;
import com.waqiti.payment.service.*;
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

/**
 * Production-grade Kafka consumer for async reversal pending events
 * Tracks and manages pending payment reversals with enterprise patterns
 * 
 * Critical for: Payment integrity, reversal tracking, compliance reporting
 * SLA: Must process pending reversals within 30 seconds for proper tracking
 */
@Component
@Slf4j
public class AsyncReversalPendingConsumer {

    private final AsyncReversalRepository reversalRepository;
    private final PaymentService paymentService;
    private final ReversalTrackingService reversalTrackingService;
    private final NotificationService notificationService;
    private final ComplianceReportingService complianceService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private final Counter pendingReversalCounter;
    private final Counter highValuePendingCounter;
    private final Counter compliancePendingCounter;
    private final Timer pendingProcessingTimer;
    
    private final Map<String, String> processedEvents = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_MS = TimeUnit.HOURS.toMillis(24);
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("10000.00");

    public AsyncReversalPendingConsumer(
            AsyncReversalRepository reversalRepository,
            PaymentService paymentService,
            ReversalTrackingService reversalTrackingService,
            NotificationService notificationService,
            ComplianceReportingService complianceService,
            AuditService auditService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        
        this.reversalRepository = reversalRepository;
        this.paymentService = paymentService;
        this.reversalTrackingService = reversalTrackingService;
        this.notificationService = notificationService;
        this.complianceService = complianceService;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        
        this.pendingReversalCounter = Counter.builder("async.reversal.pending.events")
            .description("Count of async reversal pending events")
            .register(meterRegistry);
        
        this.highValuePendingCounter = Counter.builder("async.reversal.pending.high.value.events")
            .description("Count of high-value pending reversal events")
            .register(meterRegistry);
        
        this.compliancePendingCounter = Counter.builder("async.reversal.pending.compliance.events")
            .description("Count of compliance-related pending reversals")
            .register(meterRegistry);
        
        this.pendingProcessingTimer = Timer.builder("async.reversal.pending.processing.duration")
            .description("Time taken to process pending reversal events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = "async-reversal-pending",
        groupId = "async-reversal-pending-processor-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltTopic = "async-reversal-pending-dlt",
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void handleAsyncReversalPendingEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start();
        String eventId = String.format("%s-%d-%d-%d", topic, partition, offset, System.currentTimeMillis());
        
        log.info("Received async reversal pending event - eventId: {}, partition: {}, offset: {}", 
            eventId, partition, offset);
        
        try {
            if (isAlreadyProcessed(eventId)) {
                log.info("Event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }
            
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            
            String reversalId = (String) eventData.get("reversalId");
            String originalTransactionId = (String) eventData.get("originalTransactionId");
            String paymentId = (String) eventData.get("paymentId");
            BigDecimal amount = new BigDecimal(eventData.get("amount").toString());
            String currency = (String) eventData.get("currency");
            String reversalType = (String) eventData.get("reversalType");
            String reason = (String) eventData.get("reason");
            String pendingReason = (String) eventData.get("pendingReason");
            String priority = (String) eventData.getOrDefault("priority", "NORMAL");
            Boolean requiresApproval = (Boolean) eventData.getOrDefault("requiresApproval", false);
            Boolean isComplianceRelated = (Boolean) eventData.getOrDefault("isComplianceRelated", false);
            
            String correlationId = String.format("async-reversal-pending-%s-%d", 
                reversalId, System.currentTimeMillis());
            
            log.warn("CRITICAL: Processing async reversal pending - reversalId: {}, paymentId: {}, amount: {}, reason: {}, correlationId: {}", 
                reversalId, paymentId, amount, pendingReason, correlationId);
            
            pendingReversalCounter.increment();
            
            processAsyncReversalPending(reversalId, originalTransactionId, paymentId, amount, currency,
                reversalType, reason, pendingReason, priority, requiresApproval, isComplianceRelated,
                eventData, correlationId);
            
            markAsProcessed(eventId);
            acknowledgment.acknowledge();
            
            sample.stop(pendingProcessingTimer);
            
            log.info("Successfully processed async reversal pending event: {}", eventId);
            
        } catch (Exception e) {
            log.error("Failed to process async reversal pending event {}: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Async reversal pending processing failed", e);
        }
    }

    @CircuitBreaker(name = "async-reversal", fallbackMethod = "processAsyncReversalPendingFallback")
    @Retry(name = "async-reversal")
    private void processAsyncReversalPending(
            String reversalId,
            String originalTransactionId,
            String paymentId,
            BigDecimal amount,
            String currency,
            String reversalType,
            String reason,
            String pendingReason,
            String priority,
            Boolean requiresApproval,
            Boolean isComplianceRelated,
            Map<String, Object> eventData,
            String correlationId) {
        
        // Create or update async reversal tracking record
        AsyncReversal asyncReversal = AsyncReversal.builder()
            .id(reversalId)
            .originalTransactionId(originalTransactionId)
            .paymentId(paymentId)
            .amount(amount)
            .currency(currency)
            .reversalType(ReversalType.fromString(reversalType))
            .reason(reason)
            .pendingReason(pendingReason)
            .priority(priority)
            .status(AsyncReversalStatus.PENDING)
            .requiresApproval(requiresApproval)
            .isComplianceRelated(isComplianceRelated)
            .createdAt(LocalDateTime.now())
            .lastUpdatedAt(LocalDateTime.now())
            .build();
        
        reversalRepository.save(asyncReversal);
        
        // Track high-value reversals
        if (amount.compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            highValuePendingCounter.increment();
            handleHighValuePendingReversal(asyncReversal, correlationId);
        }
        
        // Track compliance-related reversals
        if (isComplianceRelated) {
            compliancePendingCounter.increment();
            handleCompliancePendingReversal(asyncReversal, correlationId);
        }
        
        // Setup monitoring and alerts
        setupReversalMonitoring(asyncReversal, correlationId);
        
        // Notify relevant stakeholders
        sendPendingReversalNotifications(asyncReversal, correlationId);
        
        // Update payment status
        if (paymentId != null) {
            paymentService.updatePaymentStatus(paymentId, PaymentStatus.REVERSAL_PENDING, 
                "Async reversal pending: " + pendingReason);
        }
        
        // Create reversal tracking workflow
        reversalTrackingService.createTrackingWorkflow(asyncReversal, correlationId);
        
        // Publish tracking event
        kafkaTemplate.send("async-reversal-tracking-updates", Map.of(
            "reversalId", reversalId,
            "originalTransactionId", originalTransactionId,
            "paymentId", paymentId,
            "status", "PENDING",
            "pendingReason", pendingReason,
            "amount", amount,
            "currency", currency,
            "priority", priority,
            "requiresApproval", requiresApproval,
            "isComplianceRelated", isComplianceRelated,
            "eventType", "REVERSAL_PENDING_CREATED",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Audit the pending reversal
        auditService.logAsyncReversalEvent(
            "ASYNC_REVERSAL_PENDING_CREATED",
            reversalId,
            Map.of(
                "originalTransactionId", originalTransactionId,
                "paymentId", paymentId,
                "amount", amount,
                "currency", currency,
                "reversalType", reversalType,
                "reason", reason,
                "pendingReason", pendingReason,
                "priority", priority,
                "requiresApproval", requiresApproval,
                "isComplianceRelated", isComplianceRelated,
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            )
        );
        
        log.warn("CRITICAL: Async reversal marked as pending - reversalId: {}, paymentId: {}, reason: {}, correlationId: {}", 
            reversalId, paymentId, pendingReason, correlationId);
    }

    private void handleHighValuePendingReversal(AsyncReversal asyncReversal, String correlationId) {
        log.error("HIGH VALUE ALERT: High-value async reversal pending - reversalId: {}, amount: {}, correlationId: {}", 
            asyncReversal.getId(), asyncReversal.getAmount(), correlationId);
        
        // Create high-priority approval workflow
        reversalTrackingService.createHighValueApprovalWorkflow(asyncReversal, correlationId);
        
        // Send high-value alert
        kafkaTemplate.send("high-value-reversal-alerts", Map.of(
            "reversalId", asyncReversal.getId(),
            "originalTransactionId", asyncReversal.getOriginalTransactionId(),
            "paymentId", asyncReversal.getPaymentId(),
            "amount", asyncReversal.getAmount(),
            "currency", asyncReversal.getCurrency(),
            "pendingReason", asyncReversal.getPendingReason(),
            "priority", "URGENT",
            "requiresApproval", true,
            "alertType", "HIGH_VALUE_REVERSAL_PENDING",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Notify executive team
        notificationService.sendExecutiveAlert(
            "High-Value Reversal Pending",
            String.format("High-value async reversal pending: %s for amount %s %s", 
                asyncReversal.getId(), asyncReversal.getAmount(), asyncReversal.getCurrency()),
            Map.of(
                "reversalId", asyncReversal.getId(),
                "amount", asyncReversal.getAmount(),
                "currency", asyncReversal.getCurrency(),
                "correlationId", correlationId
            )
        );
    }

    private void handleCompliancePendingReversal(AsyncReversal asyncReversal, String correlationId) {
        log.error("COMPLIANCE ALERT: Compliance-related async reversal pending - reversalId: {}, correlationId: {}", 
            asyncReversal.getId(), correlationId);
        
        // Report to compliance team
        complianceService.reportPendingReversal(asyncReversal, correlationId);
        
        // Send compliance alert
        kafkaTemplate.send("compliance-reversal-alerts", Map.of(
            "reversalId", asyncReversal.getId(),
            "originalTransactionId", asyncReversal.getOriginalTransactionId(),
            "paymentId", asyncReversal.getPaymentId(),
            "amount", asyncReversal.getAmount(),
            "currency", asyncReversal.getCurrency(),
            "pendingReason", asyncReversal.getPendingReason(),
            "priority", "HIGH",
            "alertType", "COMPLIANCE_REVERSAL_PENDING",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Notify compliance team
        notificationService.sendComplianceAlert(
            "Compliance Reversal Pending",
            String.format("Compliance-related async reversal pending: %s", asyncReversal.getId()),
            Map.of(
                "reversalId", asyncReversal.getId(),
                "pendingReason", asyncReversal.getPendingReason(),
                "correlationId", correlationId
            )
        );
    }

    private void setupReversalMonitoring(AsyncReversal asyncReversal, String correlationId) {
        // Setup automated monitoring and escalation
        reversalTrackingService.setupMonitoringAlerts(asyncReversal, correlationId);
        
        // Schedule periodic status checks
        kafkaTemplate.send("reversal-monitoring-schedule", Map.of(
            "reversalId", asyncReversal.getId(),
            "monitoringType", "PENDING_REVERSAL_STATUS_CHECK",
            "checkIntervalMinutes", 30,
            "escalationTimeoutHours", 24,
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void sendPendingReversalNotifications(AsyncReversal asyncReversal, String correlationId) {
        // Notify operations team
        notificationService.sendOperationsNotification(
            "Async Reversal Pending",
            String.format("Async reversal %s is pending: %s", 
                asyncReversal.getId(), asyncReversal.getPendingReason()),
            Map.of(
                "reversalId", asyncReversal.getId(),
                "paymentId", asyncReversal.getPaymentId(),
                "amount", asyncReversal.getAmount(),
                "currency", asyncReversal.getCurrency(),
                "pendingReason", asyncReversal.getPendingReason(),
                "priority", asyncReversal.getPriority(),
                "correlationId", correlationId
            )
        );
        
        // Send in-app notification if customer-facing
        if ("CUSTOMER_DISPUTE".equals(asyncReversal.getReversalType().toString()) ||
            "CHARGEBACK".equals(asyncReversal.getReversalType().toString())) {
            
            kafkaTemplate.send("customer-reversal-notifications", Map.of(
                "reversalId", asyncReversal.getId(),
                "paymentId", asyncReversal.getPaymentId(),
                "status", "PENDING",
                "message", "Your reversal request is being processed",
                "estimatedResolutionHours", 48,
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
        }
    }

    private void processAsyncReversalPendingFallback(
            String reversalId,
            String originalTransactionId,
            String paymentId,
            BigDecimal amount,
            String currency,
            String reversalType,
            String reason,
            String pendingReason,
            String priority,
            Boolean requiresApproval,
            Boolean isComplianceRelated,
            Map<String, Object> eventData,
            String correlationId,
            Exception e) {
        
        log.error("Circuit breaker activated for async reversal pending - reversalId: {}, correlationId: {}, error: {}", 
            reversalId, correlationId, e.getMessage());
        
        Map<String, Object> fallbackEvent = new HashMap<>();
        fallbackEvent.put("reversalId", reversalId);
        fallbackEvent.put("originalTransactionId", originalTransactionId);
        fallbackEvent.put("paymentId", paymentId);
        fallbackEvent.put("amount", amount);
        fallbackEvent.put("currency", currency);
        fallbackEvent.put("reversalType", reversalType);
        fallbackEvent.put("reason", reason);
        fallbackEvent.put("pendingReason", pendingReason);
        fallbackEvent.put("priority", priority);
        fallbackEvent.put("requiresApproval", requiresApproval);
        fallbackEvent.put("isComplianceRelated", isComplianceRelated);
        fallbackEvent.put("correlationId", correlationId);
        fallbackEvent.put("error", e.getMessage());
        fallbackEvent.put("fallbackTimestamp", Instant.now().toString());
        
        kafkaTemplate.send("async-reversal-pending-fallback-events", fallbackEvent);
    }

    @DltHandler
    public void handleDlt(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        log.error("CRITICAL: Async reversal pending message sent to DLT - topic: {}, partition: {}, offset: {}, error: {}", 
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
            
            kafkaTemplate.send("async-reversal-pending-processing-failures", dltEvent);
            
            notificationService.sendCriticalOperationalAlert(
                "Async Reversal Pending Processing Failed",
                String.format("CRITICAL: Failed to process async reversal pending after max retries. Error: %s", exceptionMessage),
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
        
        // Cleanup old entries
        processedEvents.entrySet().removeIf(entry -> {
            long timestamp = Long.parseLong(entry.getValue());
            return System.currentTimeMillis() - timestamp > IDEMPOTENCY_TTL_MS;
        });
    }
}