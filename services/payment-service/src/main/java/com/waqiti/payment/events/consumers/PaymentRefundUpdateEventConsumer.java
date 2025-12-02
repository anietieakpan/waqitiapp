package com.waqiti.payment.events.consumers;

import com.waqiti.payment.client.AnalyticsServiceClient;
import com.waqiti.payment.client.LedgerServiceClient;
import com.waqiti.payment.client.NotificationServiceClient;
import com.waqiti.payment.dto.analytics.RecordRefundAnalyticsRequest;
import com.waqiti.payment.dto.ledger.RecordRefundUpdateRequest;
import com.waqiti.payment.dto.ledger.RecordRefundUpdateResponse;
import com.waqiti.payment.dto.notification.RefundStatusNotificationRequest;
import com.waqiti.payment.events.PaymentRefundUpdateEvent;
import com.waqiti.payment.exception.ServiceIntegrationException;
import com.waqiti.payment.service.AuditService;
import com.waqiti.payment.service.IdempotencyService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Payment Refund Update Event Consumer
 * 
 * CRITICAL CONSUMER - Processes payment refund status update events
 * 
 * EVENT SOURCE:
 * - PaymentService.updateRefundStatus(): Line 596 publishes refund status updates
 * 
 * BUSINESS CRITICALITY:
 * - Tracks refund lifecycle from initiation to completion
 * - Maintains financial accuracy for refund accounting
 * - Notifies customers of refund progress
 * - Supports compliance with refund SLAs
 * - Enables real-time refund monitoring and reporting
 * 
 * REFUND STATUS TRANSITIONS:
 * - INITIATED: Refund request created
 * - PROCESSING: Refund being processed by gateway
 * - PENDING: Awaiting gateway confirmation
 * - COMPLETED: Refund successfully processed
 * - FAILED: Refund processing failed
 * - CANCELLED: Refund request cancelled
 * 
 * PROCESSING ACTIONS:
 * - Record refund status updates in ledger for financial tracking
 * - Send customer notifications for status changes
 * - Capture refund analytics for reporting
 * - Audit all refund transitions for compliance
 * - Track refund processing times and SLAs
 * - Alert on failed or delayed refunds
 * 
 * BUSINESS VALUE:
 * - Customer trust: Transparent refund process
 * - Financial accuracy: Complete refund accounting
 * - Compliance: Audit trail for regulatory requirements
 * - Operations: Real-time refund monitoring
 * - Analytics: Refund patterns and performance metrics
 * 
 * FAILURE IMPACT:
 * - Loss of refund status visibility
 * - Missing customer notifications
 * - Incomplete financial records
 * - Compliance gaps in refund tracking
 * - Reduced ability to meet refund SLAs
 * 
 * RESILIENCE:
 * - Idempotency with 24-hour TTL
 * - Circuit breakers on all external calls
 * - Critical ledger recording (hard fail)
 * - Graceful degradation for analytics
 * - Dead Letter Queue for failed events
 * - Comprehensive audit logging
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-27
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentRefundUpdateEventConsumer {
    
    private final LedgerServiceClient ledgerServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final AnalyticsServiceClient analyticsServiceClient;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    
    private static final String TOPIC_NAME = "payment-refund-updates";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    
    @KafkaListener(
        topics = "${kafka.topics.payment-refund-updates:payment-refund-updates}",
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
    public void handlePaymentRefundUpdate(
            @Payload PaymentRefundUpdateEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            Acknowledgment acknowledgment) {
        
        long startTime = System.currentTimeMillis();
        String correlationId = event.getCorrelationId() != null ? 
                event.getCorrelationId() : UUID.randomUUID().toString();
        
        log.info("Received payment refund update event - eventId: {}, correlationId: {}, " +
                "topic: {}, partition: {}, offset: {}, refundId: {}, paymentId: {}, " +
                "status: {}, previousStatus: {}", 
                event.getEventId(), correlationId, topic, partition, offset, event.getRefundId(),
                event.getPaymentId(), event.getStatus(), event.getPreviousStatus());
        
        try {
            if (event.getRefundId() == null || event.getPaymentId() == null) {
                log.error("Invalid payment refund update event - missing required fields: refundId={}, paymentId={}",
                        event.getRefundId(), event.getPaymentId());
                auditService.logEventProcessingFailure(
                    event.getEventId() != null ? event.getEventId() : "UNKNOWN", 
                    TOPIC_NAME, 
                    "VALIDATION_FAILED",
                    "Missing required fields: refundId or paymentId",
                    correlationId,
                    Map.of("event", event)
                );
                acknowledgment.acknowledge();
                return;
            }
            
            String eventId = event.getEventId() != null ? event.getEventId() : 
                    UUID.randomUUID().toString();
            
            if (idempotencyService.isDuplicate(eventId, IDEMPOTENCY_TTL)) {
                log.warn("Duplicate payment refund update event detected - eventId: {}, correlationId: {}", 
                        eventId, correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            processRefundUpdate(event, correlationId);
            
            idempotencyService.recordProcessedEvent(eventId, IDEMPOTENCY_TTL);
            
            long processingTime = System.currentTimeMillis() - startTime;
            log.info("Successfully processed payment refund update event - eventId: {}, correlationId: {}, " +
                    "refundId: {}, paymentId: {}, status: {}, processingTimeMs: {}", 
                    eventId, correlationId, event.getRefundId(), event.getPaymentId(),
                    event.getStatus(), processingTime);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("Failed to process payment refund update event - eventId: {}, correlationId: {}, " +
                    "refundId: {}, error: {}, processingTimeMs: {}", 
                    event.getEventId(), correlationId, event.getRefundId(), 
                    e.getMessage(), processingTime, e);
            
            auditService.logEventProcessingFailure(
                event.getEventId() != null ? event.getEventId() : "UNKNOWN",
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
                "Failed to process payment refund update event: " + e.getMessage(), e);
        }
    }
    
    private void processRefundUpdate(PaymentRefundUpdateEvent event, String correlationId) {
        long startTime = System.currentTimeMillis();
        
        log.info("Processing payment refund update - refundId: {}, paymentId: {}, status: {}, " +
                "previousStatus: {}, refundAmount: {}, correlationId: {}",
                event.getRefundId(), event.getPaymentId(), event.getStatus(),
                event.getPreviousStatus(), event.getRefundAmount(), correlationId);
        
        recordRefundUpdateInLedger(event, correlationId);
        
        sendRefundStatusNotifications(event, correlationId);
        
        recordRefundAnalytics(event, correlationId);
        
        long processingTime = System.currentTimeMillis() - startTime;
        
        Map<String, Object> auditMetadata = new HashMap<>();
        auditMetadata.put("refundId", event.getRefundId());
        auditMetadata.put("paymentId", event.getPaymentId());
        auditMetadata.put("status", event.getStatus());
        auditMetadata.put("previousStatus", event.getPreviousStatus());
        auditMetadata.put("refundAmount", event.getRefundAmount());
        auditMetadata.put("refundReason", event.getRefundReason());
        auditMetadata.put("refundType", event.getRefundType());
        auditMetadata.put("gatewayId", event.getGatewayId());
        auditMetadata.put("processedBy", event.getProcessedBy());
        auditMetadata.put("failureReason", event.getFailureReason());
        if (event.getMetadata() != null) {
            auditMetadata.putAll(event.getMetadata());
        }
        
        auditService.logPaymentRefundUpdate(
            event.getEventId() != null ? event.getEventId() : UUID.randomUUID().toString(),
            event.getRefundId(),
            event.getPaymentId(),
            event.getStatus(),
            event.getPreviousStatus(),
            event.getRefundAmount(),
            event.getRefundReason(),
            event.getProcessedBy(),
            processingTime,
            correlationId,
            auditMetadata
        );
        
        log.info("Payment refund update processed successfully - refundId: {}, paymentId: {}, " +
                "status: {}, processingTimeMs: {}, correlationId: {}",
                event.getRefundId(), event.getPaymentId(), event.getStatus(), 
                processingTime, correlationId);
    }
    
    @CircuitBreaker(name = "ledger-service", fallbackMethod = "recordRefundUpdateInLedgerFallback")
    @Retry(name = "ledger-service")
    @TimeLimiter(name = "ledger-service")
    private void recordRefundUpdateInLedger(PaymentRefundUpdateEvent event, String correlationId) {
        log.info("Recording refund update in ledger - refundId: {}, status: {}, refundAmount: {}, correlationId: {}",
                event.getRefundId(), event.getStatus(), event.getRefundAmount(), correlationId);
        
        RecordRefundUpdateRequest request = RecordRefundUpdateRequest.builder()
                .refundId(event.getRefundId())
                .paymentId(event.getPaymentId())
                .customerId(event.getCustomerId())
                .merchantId(event.getMerchantId())
                .status(event.getStatus())
                .previousStatus(event.getPreviousStatus())
                .refundAmount(event.getRefundAmount())
                .currency(event.getCurrency())
                .refundReason(event.getRefundReason())
                .refundType(event.getRefundType())
                .gatewayId(event.getGatewayId())
                .gatewayRefundId(event.getGatewayRefundId())
                .processedBy(event.getProcessedBy())
                .completedAt(event.getCompletedAt())
                .updatedAt(event.getUpdatedAt())
                .failureReason(event.getFailureReason())
                .correlationId(correlationId)
                .build();
        
        try {
            RecordRefundUpdateResponse response = ledgerServiceClient.recordRefundUpdate(request);
            log.info("Refund update recorded in ledger successfully - refundId: {}, ledgerEntryId: {}, correlationId: {}",
                    event.getRefundId(), response.getLedgerEntryId(), correlationId);
        } catch (Exception e) {
            log.error("Failed to record refund update in ledger - refundId: {}, error: {}, correlationId: {}",
                    event.getRefundId(), e.getMessage(), correlationId, e);
            throw e;
        }
    }
    
    private void recordRefundUpdateInLedgerFallback(PaymentRefundUpdateEvent event, String correlationId,
                                                     Throwable throwable) {
        log.error("CRITICAL: Ledger service unavailable for refund update recording - refundId: {}, " +
                "paymentId: {}, status: {}, error: {}, correlationId: {}",
                event.getRefundId(), event.getPaymentId(), event.getStatus(), 
                throwable.getMessage(), correlationId);
        
        auditService.logCriticalLedgerFailure(
            "RECORD_REFUND_UPDATE",
            event.getCustomerId(),
            event.getPaymentId(),
            throwable.getMessage(),
            correlationId,
            Map.of(
                "refundId", event.getRefundId(),
                "status", event.getStatus(),
                "refundAmount", event.getRefundAmount() != null ? event.getRefundAmount() : "NULL"
            )
        );
        
        throw new ServiceIntegrationException(
            "Critical ledger service failure for refund update: " + throwable.getMessage(), throwable);
    }
    
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendRefundStatusNotificationsFallback")
    @Retry(name = "notification-service")
    @TimeLimiter(name = "notification-service")
    private void sendRefundStatusNotifications(PaymentRefundUpdateEvent event, String correlationId) {
        if (!shouldNotifyCustomer(event)) {
            log.debug("Skipping customer notification for refund update - refundId: {}, status: {}, correlationId: {}",
                    event.getRefundId(), event.getStatus(), correlationId);
            return;
        }
        
        log.info("Sending refund status notifications - refundId: {}, customerId: {}, status: {}, correlationId: {}",
                event.getRefundId(), event.getCustomerId(), event.getStatus(), correlationId);
        
        RefundStatusNotificationRequest request = RefundStatusNotificationRequest.builder()
                .userId(event.getCustomerId())
                .refundId(event.getRefundId())
                .paymentId(event.getPaymentId())
                .status(event.getStatus())
                .refundAmount(event.getRefundAmount())
                .currency(event.getCurrency())
                .refundReason(event.getRefundReason())
                .completedAt(event.getCompletedAt())
                .failureReason(event.getFailureReason())
                .channels(determineNotificationChannels(event))
                .correlationId(correlationId)
                .build();
        
        try {
            notificationServiceClient.sendRefundStatusNotification(request);
            log.info("Refund status notifications sent successfully - refundId: {}, status: {}, correlationId: {}",
                    event.getRefundId(), event.getStatus(), correlationId);
        } catch (Exception e) {
            log.error("Failed to send refund status notifications - refundId: {}, error: {}, correlationId: {}",
                    event.getRefundId(), e.getMessage(), correlationId, e);
            throw e;
        }
    }
    
    private void sendRefundStatusNotificationsFallback(PaymentRefundUpdateEvent event, String correlationId,
                                                         Throwable throwable) {
        log.warn("Notification service unavailable for refund status notifications - refundId: {}, error: {}, correlationId: {}",
                event.getRefundId(), throwable.getMessage(), correlationId);
    }
    
    private boolean shouldNotifyCustomer(PaymentRefundUpdateEvent event) {
        if (event.getCustomerId() == null) {
            return false;
        }
        
        String status = event.getStatus();
        return "COMPLETED".equals(status) || 
               "FAILED".equals(status) || 
               "CANCELLED".equals(status);
    }
    
    private List<String> determineNotificationChannels(PaymentRefundUpdateEvent event) {
        if ("COMPLETED".equals(event.getStatus())) {
            return List.of("EMAIL", "PUSH", "IN_APP");
        } else if ("FAILED".equals(event.getStatus())) {
            return List.of("EMAIL", "IN_APP");
        }
        return List.of("IN_APP");
    }
    
    @CircuitBreaker(name = "analytics-service", fallbackMethod = "recordRefundAnalyticsFallback")
    @Retry(name = "analytics-service")
    @TimeLimiter(name = "analytics-service")
    private void recordRefundAnalytics(PaymentRefundUpdateEvent event, String correlationId) {
        log.info("Recording refund analytics - refundId: {}, status: {}, correlationId: {}",
                event.getRefundId(), event.getStatus(), correlationId);
        
        RecordRefundAnalyticsRequest request = RecordRefundAnalyticsRequest.builder()
                .refundId(event.getRefundId())
                .paymentId(event.getPaymentId())
                .customerId(event.getCustomerId())
                .merchantId(event.getMerchantId())
                .status(event.getStatus())
                .previousStatus(event.getPreviousStatus())
                .refundAmount(event.getRefundAmount())
                .currency(event.getCurrency())
                .refundReason(event.getRefundReason())
                .refundType(event.getRefundType())
                .gatewayId(event.getGatewayId())
                .processedBy(event.getProcessedBy())
                .initiatedAt(event.getInitiatedAt())
                .processedAt(event.getProcessedAt())
                .completedAt(event.getCompletedAt())
                .failureReason(event.getFailureReason())
                .correlationId(correlationId)
                .build();
        
        try {
            analyticsServiceClient.recordRefundAnalytics(request);
            log.info("Refund analytics recorded successfully - refundId: {}, status: {}, correlationId: {}",
                    event.getRefundId(), event.getStatus(), correlationId);
        } catch (Exception e) {
            log.error("Failed to record refund analytics - refundId: {}, error: {}, correlationId: {}",
                    event.getRefundId(), e.getMessage(), correlationId, e);
            throw e;
        }
    }
    
    private void recordRefundAnalyticsFallback(PaymentRefundUpdateEvent event, String correlationId,
                                                Throwable throwable) {
        log.warn("Analytics service unavailable for refund analytics - refundId: {}, error: {}, correlationId: {}",
                event.getRefundId(), throwable.getMessage(), correlationId);
    }
    
    @DltHandler
    public void handleDeadLetterTopic(
            @Payload PaymentRefundUpdateEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        String correlationId = event.getCorrelationId() != null ? 
                event.getCorrelationId() : UUID.randomUUID().toString();
        
        log.error("Payment refund update event moved to DLT - eventId: {}, correlationId: {}, " +
                "originalTopic: {}, refundId: {}, paymentId: {}, status: {}, error: {}",
                event.getEventId(), correlationId, topic, event.getRefundId(), 
                event.getPaymentId(), event.getStatus(), exceptionMessage);
        
        Map<String, Object> dltMetadata = new HashMap<>();
        dltMetadata.put("originalTopic", topic);
        dltMetadata.put("refundId", event.getRefundId());
        dltMetadata.put("paymentId", event.getPaymentId());
        dltMetadata.put("status", event.getStatus());
        dltMetadata.put("previousStatus", event.getPreviousStatus());
        dltMetadata.put("refundAmount", event.getRefundAmount());
        dltMetadata.put("exceptionMessage", exceptionMessage);
        dltMetadata.put("eventTimestamp", event.getTimestamp());
        dltMetadata.put("dltTimestamp", Instant.now());
        
        auditService.logDeadLetterEvent(
            event.getEventId() != null ? event.getEventId() : "UNKNOWN",
            TOPIC_NAME,
            "PAYMENT_REFUND_UPDATE_DLT",
            exceptionMessage,
            correlationId,
            dltMetadata
        );
    }
}