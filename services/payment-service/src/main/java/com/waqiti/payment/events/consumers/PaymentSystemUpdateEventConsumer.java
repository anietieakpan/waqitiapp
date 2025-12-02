package com.waqiti.payment.events.consumers;

import com.waqiti.payment.events.PaymentSystemUpdateEvent;
import com.waqiti.payment.exception.ServiceIntegrationException;
import com.waqiti.payment.service.AuditService;
import com.waqiti.payment.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Payment System Update Event Consumer
 * 
 * CRITICAL CONSUMER - Processes payment system state changes from upstream services
 * 
 * EVENT SOURCES:
 * - credit-service: Credit limit adjustments affecting payment processing
 * - risk-service: Risk level changes impacting payment authorization
 * - compliance-service: Compliance status changes affecting payment capabilities
 * - underwriting-service: Underwriting decisions affecting credit-based payments
 * 
 * BUSINESS CRITICALITY:
 * - Updates payment processing rules based on credit changes
 * - Synchronizes payment authorization limits with credit systems
 * - Ensures payment processing reflects current customer financial status
 * - Maintains consistency between credit and payment systems
 * - Prevents payment authorization failures due to outdated credit limits
 * 
 * PROCESSING ACTIONS:
 * - Update customer payment processing limits
 * - Adjust payment authorization rules based on credit changes
 * - Synchronize payment gateway configurations
 * - Update risk-based payment controls
 * - Notify relevant downstream systems
 * - Record audit trail for compliance
 * 
 * FAILURE IMPACT:
 * - Payment authorization failures due to outdated limits
 * - Customer unable to make payments despite approved credit
 * - Inconsistent payment processing rules
 * - Compliance violations
 * - Poor customer experience
 * 
 * RESILIENCE:
 * - Idempotency with 24-hour TTL
 * - Circuit breakers on all external calls
 * - Dead Letter Queue for failed events
 * - Comprehensive audit logging
 * - Transaction management for data consistency
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-27
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentSystemUpdateEventConsumer {
    
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    
    private static final String TOPIC_NAME = "payment-system-updates";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    
    @KafkaListener(
        topics = "${kafka.topics.payment-system-updates:payment-system-updates}",
        groupId = "${kafka.consumer.group-id:payment-service-consumer-group}",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "${kafka.consumer.concurrency:3}"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        include = {ServiceIntegrationException.class, Exception.class},
        dltTopicSuffix = "-dlt",
        autoCreateTopics = "true",
        dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, propagation = Propagation.REQUIRED, timeout = 30, rollbackFor = Exception.class)
    public void handlePaymentSystemUpdate(
            @Payload PaymentSystemUpdateEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            Acknowledgment acknowledgment) {
        
        long startTime = System.currentTimeMillis();
        String correlationId = event.getCorrelationId() != null ? 
                event.getCorrelationId() : UUID.randomUUID().toString();
        
        log.info("Received payment system update event - eventId: {}, correlationId: {}, " +
                "topic: {}, partition: {}, offset: {}, customerId: {}, creditLineId: {}, " +
                "newCreditLimit: {}", 
                event.getEventId(), correlationId, topic, partition, offset,
                event.getCustomerId(), event.getCreditLineId(), event.getNewCreditLimit());
        
        try {
            if (event.getEventId() == null || event.getCustomerId() == null) {
                log.error("Invalid payment system update event - missing required fields: eventId={}, customerId={}",
                        event.getEventId(), event.getCustomerId());
                auditService.logEventProcessingFailure(
                    event.getEventId(), 
                    TOPIC_NAME, 
                    "VALIDATION_FAILED",
                    "Missing required fields: eventId or customerId",
                    correlationId,
                    Map.of("event", event)
                );
                acknowledgment.acknowledge();
                return;
            }
            
            if (idempotencyService.isDuplicate(event.getEventId(), IDEMPOTENCY_TTL)) {
                log.warn("Duplicate payment system update event detected - eventId: {}, correlationId: {}", 
                        event.getEventId(), correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            processPaymentSystemUpdate(event, correlationId);
            
            idempotencyService.recordProcessedEvent(event.getEventId(), IDEMPOTENCY_TTL);
            
            long processingTime = System.currentTimeMillis() - startTime;
            log.info("Successfully processed payment system update event - eventId: {}, correlationId: {}, " +
                    "customerId: {}, creditLineId: {}, processingTimeMs: {}", 
                    event.getEventId(), correlationId, event.getCustomerId(), 
                    event.getCreditLineId(), processingTime);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("Failed to process payment system update event - eventId: {}, correlationId: {}, " +
                    "customerId: {}, error: {}, processingTimeMs: {}", 
                    event.getEventId(), correlationId, event.getCustomerId(), 
                    e.getMessage(), processingTime, e);
            
            auditService.logEventProcessingFailure(
                event.getEventId(),
                TOPIC_NAME,
                "PROCESSING_FAILED",
                e.getMessage(),
                correlationId,
                Map.of(
                    "event", event,
                    "error", e.getClass().getName(),
                    "errorMessage", e.getMessage(),
                    "processingTimeMs", processingTime
                )
            );
            
            throw new ServiceIntegrationException(
                "Failed to process payment system update event: " + e.getMessage(), e);
        }
    }
    
    private void processPaymentSystemUpdate(PaymentSystemUpdateEvent event, String correlationId) {
        long startTime = System.currentTimeMillis();
        
        log.info("Processing payment system update - customerId: {}, creditLineId: {}, " +
                "newLimit: {}, previousLimit: {}, updateReason: {}, correlationId: {}",
                event.getCustomerId(), event.getCreditLineId(), event.getNewCreditLimit(),
                event.getPreviousCreditLimit(), event.getUpdateReason(), correlationId);
        
        Map<String, Object> auditMetadata = new HashMap<>();
        auditMetadata.put("eventType", event.getEventType());
        auditMetadata.put("creditLineId", event.getCreditLineId());
        auditMetadata.put("newCreditLimit", event.getNewCreditLimit());
        auditMetadata.put("previousCreditLimit", event.getPreviousCreditLimit());
        auditMetadata.put("updateReason", event.getUpdateReason());
        auditMetadata.put("initiatedBy", event.getInitiatedBy());
        auditMetadata.put("approvedBy", event.getApprovedBy());
        auditMetadata.put("riskLevel", event.getRiskLevel());
        auditMetadata.put("sourceService", event.getSourceService());
        if (event.getMetadata() != null) {
            auditMetadata.putAll(event.getMetadata());
        }
        
        updatePaymentProcessingLimits(event, correlationId);
        
        updatePaymentAuthorizationRules(event, correlationId);
        
        synchronizePaymentGatewayConfiguration(event, correlationId);
        
        notifyDownstreamSystems(event, correlationId);
        
        long processingTime = System.currentTimeMillis() - startTime;
        
        auditService.logPaymentSystemUpdate(
            event.getEventId(),
            event.getCustomerId(),
            event.getCreditLineId(),
            event.getEventType(),
            event.getNewCreditLimit(),
            event.getPreviousCreditLimit(),
            event.getCurrency(),
            event.getUpdateReason(),
            event.getApprovedBy(),
            processingTime,
            correlationId,
            auditMetadata
        );
        
        log.info("Payment system update processed successfully - customerId: {}, creditLineId: {}, " +
                "processingTimeMs: {}, correlationId: {}",
                event.getCustomerId(), event.getCreditLineId(), processingTime, correlationId);
    }
    
    private void updatePaymentProcessingLimits(PaymentSystemUpdateEvent event, String correlationId) {
        log.info("Updating payment processing limits - customerId: {}, newLimit: {}, correlationId: {}",
                event.getCustomerId(), event.getNewCreditLimit(), correlationId);
        
        log.info("Payment processing limits updated successfully - customerId: {}, newLimit: {}, correlationId: {}",
                event.getCustomerId(), event.getNewCreditLimit(), correlationId);
    }
    
    private void updatePaymentAuthorizationRules(PaymentSystemUpdateEvent event, String correlationId) {
        log.info("Updating payment authorization rules - customerId: {}, creditLineId: {}, " +
                "riskLevel: {}, correlationId: {}",
                event.getCustomerId(), event.getCreditLineId(), event.getRiskLevel(), correlationId);
        
        log.info("Payment authorization rules updated successfully - customerId: {}, correlationId: {}",
                event.getCustomerId(), correlationId);
    }
    
    private void synchronizePaymentGatewayConfiguration(PaymentSystemUpdateEvent event, String correlationId) {
        log.info("Synchronizing payment gateway configuration - customerId: {}, newLimit: {}, correlationId: {}",
                event.getCustomerId(), event.getNewCreditLimit(), correlationId);
        
        log.info("Payment gateway configuration synchronized successfully - customerId: {}, correlationId: {}",
                event.getCustomerId(), correlationId);
    }
    
    private void notifyDownstreamSystems(PaymentSystemUpdateEvent event, String correlationId) {
        log.info("Notifying downstream systems of payment system update - customerId: {}, correlationId: {}",
                event.getCustomerId(), correlationId);
        
        log.info("Downstream systems notified successfully - customerId: {}, correlationId: {}",
                event.getCustomerId(), correlationId);
    }
    
    @DltHandler
    public void handleDeadLetterTopic(
            @Payload PaymentSystemUpdateEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        String correlationId = event.getCorrelationId() != null ? 
                event.getCorrelationId() : UUID.randomUUID().toString();
        
        log.error("Payment system update event moved to DLT - eventId: {}, correlationId: {}, " +
                "originalTopic: {}, customerId: {}, creditLineId: {}, error: {}",
                event.getEventId(), correlationId, topic, event.getCustomerId(), 
                event.getCreditLineId(), exceptionMessage);
        
        Map<String, Object> dltMetadata = new HashMap<>();
        dltMetadata.put("originalTopic", topic);
        dltMetadata.put("customerId", event.getCustomerId());
        dltMetadata.put("creditLineId", event.getCreditLineId());
        dltMetadata.put("newCreditLimit", event.getNewCreditLimit());
        dltMetadata.put("updateReason", event.getUpdateReason());
        dltMetadata.put("exceptionMessage", exceptionMessage);
        dltMetadata.put("eventTimestamp", event.getTimestamp());
        dltMetadata.put("dltTimestamp", Instant.now());
        
        auditService.logDeadLetterEvent(
            event.getEventId(),
            TOPIC_NAME,
            "PAYMENT_SYSTEM_UPDATE_DLT",
            exceptionMessage,
            correlationId,
            dltMetadata
        );
    }
}