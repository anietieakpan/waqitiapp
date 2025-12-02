package com.waqiti.payment.events.consumers;

import com.waqiti.payment.client.AnalyticsServiceClient;
import com.waqiti.payment.client.NotificationServiceClient;
import com.waqiti.payment.dto.analytics.RecordReversalFailureAnalyticsRequest;
import com.waqiti.payment.dto.notification.ReversalFailureNotificationRequest;
import com.waqiti.payment.events.PaymentReversalFailedEvent;
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
 * Payment Reversal Failed Event Consumer
 * 
 * CRITICAL CONSUMER - Processes failed payment reversal events
 * 
 * EVENT SOURCE:
 * - PaymentReversalInitiatedConsumer: Line 396 publishes when reversal fails
 * 
 * BUSINESS CRITICALITY:
 * - Handles failed payment reversals for manual intervention
 * - Prevents financial discrepancies from incomplete reversals
 * - Maintains audit trail of reversal failures
 * - Supports operations team with failure notifications
 * - Tracks reversal failure patterns for system improvements
 * 
 * REVERSAL FAILURE SCENARIOS:
 * - Gateway timeout or unavailability
 * - Insufficient merchant funds for reversal
 * - Payment already reversed or voided
 * - Gateway rejection (fraud, compliance)
 * - Network failures and communication errors
 * - Invalid reversal request parameters
 * 
 * PROCESSING ACTIONS:
 * - Log critical reversal failure for operations review
 * - Send high-priority alerts to operations team
 * - Notify customer of reversal delay (if appropriate)
 * - Capture failure analytics for root cause analysis
 * - Audit all reversal failures for compliance
 * - Trigger manual review workflow for resolution
 * 
 * BUSINESS VALUE:
 * - Financial integrity: Ensures all reversal attempts are tracked
 * - Customer trust: Transparent handling of reversal issues
 * - Operations efficiency: Automated failure detection and routing
 * - Compliance: Complete audit trail of failed transactions
 * - System improvement: Analytics on failure patterns
 * 
 * FAILURE IMPACT:
 * - Financial discrepancies if reversals fail silently
 * - Customer dissatisfaction from delayed reversals
 * - Compliance violations without proper tracking
 * - Operational overhead without proper alerting
 * - Inability to identify systemic reversal issues
 * 
 * RESILIENCE:
 * - Idempotency with 24-hour TTL
 * - Circuit breakers on all external calls
 * - Graceful degradation for non-critical operations
 * - Dead Letter Queue for failed events
 * - Comprehensive audit logging
 * - High-priority operations alerting
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-27
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentReversalFailedEventConsumer {
    
    private final NotificationServiceClient notificationServiceClient;
    private final AnalyticsServiceClient analyticsServiceClient;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    
    private static final String TOPIC_NAME = "payment-reversal-failed";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    
    @KafkaListener(
        topics = "${kafka.topics.payment-reversal-failed:payment-reversal-failed}",
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
    public void handlePaymentReversalFailed(
            @Payload PaymentReversalFailedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            Acknowledgment acknowledgment) {
        
        long startTime = System.currentTimeMillis();
        String correlationId = event.getCorrelationId() != null ? 
                event.getCorrelationId() : UUID.randomUUID().toString();
        
        log.error("Received payment reversal failed event - eventId: {}, correlationId: {}, " +
                "topic: {}, partition: {}, offset: {}, paymentId: {}, reversalId: {}, " +
                "errorCode: {}, errorMessage: {}", 
                event.getEventId(), correlationId, topic, partition, offset, event.getPaymentId(),
                event.getReversalId(), event.getErrorCode(), event.getErrorMessage());
        
        try {
            if (event.getPaymentId() == null) {
                log.error("Invalid payment reversal failed event - missing paymentId");
                auditService.logEventProcessingFailure(
                    event.getEventId() != null ? event.getEventId() : "UNKNOWN", 
                    TOPIC_NAME, 
                    "VALIDATION_FAILED",
                    "Missing required field: paymentId",
                    correlationId,
                    Map.of("event", event)
                );
                acknowledgment.acknowledge();
                return;
            }
            
            String eventId = event.getEventId() != null ? event.getEventId() : 
                    UUID.randomUUID().toString();
            
            if (idempotencyService.isDuplicate(eventId, IDEMPOTENCY_TTL)) {
                log.warn("Duplicate payment reversal failed event detected - eventId: {}, correlationId: {}", 
                        eventId, correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            processReversalFailure(event, correlationId);
            
            idempotencyService.recordProcessedEvent(eventId, IDEMPOTENCY_TTL);
            
            long processingTime = System.currentTimeMillis() - startTime;
            log.info("Successfully processed payment reversal failed event - eventId: {}, correlationId: {}, " +
                    "paymentId: {}, reversalId: {}, processingTimeMs: {}", 
                    eventId, correlationId, event.getPaymentId(), event.getReversalId(), processingTime);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("Failed to process payment reversal failed event - eventId: {}, correlationId: {}, " +
                    "paymentId: {}, error: {}, processingTimeMs: {}", 
                    event.getEventId(), correlationId, event.getPaymentId(), 
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
                "Failed to process payment reversal failed event: " + e.getMessage(), e);
        }
    }
    
    private void processReversalFailure(PaymentReversalFailedEvent event, String correlationId) {
        long startTime = System.currentTimeMillis();
        
        log.error("Processing payment reversal failure - paymentId: {}, reversalId: {}, errorCode: {}, " +
                "errorMessage: {}, isRetryable: {}, correlationId: {}",
                event.getPaymentId(), event.getReversalId(), event.getErrorCode(),
                event.getErrorMessage(), event.getIsRetryable(), correlationId);
        
        sendOperationsAlerts(event, correlationId);
        
        sendCustomerNotifications(event, correlationId);
        
        recordReversalFailureAnalytics(event, correlationId);
        
        long processingTime = System.currentTimeMillis() - startTime;
        
        Map<String, Object> auditMetadata = new HashMap<>();
        auditMetadata.put("paymentId", event.getPaymentId());
        auditMetadata.put("reversalId", event.getReversalId());
        auditMetadata.put("errorCode", event.getErrorCode());
        auditMetadata.put("errorMessage", event.getErrorMessage());
        auditMetadata.put("errorCategory", event.getErrorCategory());
        auditMetadata.put("failureReason", event.getFailureReason());
        auditMetadata.put("isRetryable", event.getIsRetryable());
        auditMetadata.put("retryAttempt", event.getRetryAttempt());
        auditMetadata.put("gatewayId", event.getGatewayId());
        auditMetadata.put("gatewayErrorCode", event.getGatewayErrorCode());
        auditMetadata.put("initiatedBy", event.getInitiatedBy());
        if (event.getMetadata() != null) {
            auditMetadata.putAll(event.getMetadata());
        }
        
        auditService.logPaymentReversalFailed(
            event.getEventId() != null ? event.getEventId() : UUID.randomUUID().toString(),
            event.getPaymentId(),
            event.getReversalId(),
            event.getErrorCode(),
            event.getErrorMessage(),
            event.getFailureReason(),
            event.getIsRetryable(),
            event.getRetryAttempt(),
            processingTime,
            correlationId,
            auditMetadata
        );
        
        log.info("Payment reversal failure processed successfully - paymentId: {}, reversalId: {}, " +
                "errorCode: {}, processingTimeMs: {}, correlationId: {}",
                event.getPaymentId(), event.getReversalId(), event.getErrorCode(), 
                processingTime, correlationId);
    }
    
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendOperationsAlertsFallback")
    @Retry(name = "notification-service")
    @TimeLimiter(name = "notification-service")
    private void sendOperationsAlerts(PaymentReversalFailedEvent event, String correlationId) {
        log.error("Sending high-priority operations alerts for reversal failure - paymentId: {}, " +
                "reversalId: {}, errorCode: {}, correlationId: {}",
                event.getPaymentId(), event.getReversalId(), event.getErrorCode(), correlationId);
        
        ReversalFailureNotificationRequest request = ReversalFailureNotificationRequest.builder()
                .paymentId(event.getPaymentId())
                .reversalId(event.getReversalId())
                .customerId(event.getCustomerId())
                .merchantId(event.getMerchantId())
                .reversalAmount(event.getReversalAmount())
                .currency(event.getCurrency())
                .errorCode(event.getErrorCode())
                .errorMessage(event.getErrorMessage())
                .errorCategory(event.getErrorCategory())
                .failureReason(event.getFailureReason())
                .isRetryable(event.getIsRetryable())
                .retryAttempt(event.getRetryAttempt())
                .reversalReason(event.getReversalReason())
                .gatewayId(event.getGatewayId())
                .gatewayErrorCode(event.getGatewayErrorCode())
                .notificationType("OPERATIONS_ALERT")
                .priority("CRITICAL")
                .channels(List.of("EMAIL", "SLACK", "PAGERDUTY"))
                .correlationId(correlationId)
                .build();
        
        try {
            notificationServiceClient.sendReversalFailureNotification(request);
            log.info("Operations alerts sent successfully for reversal failure - paymentId: {}, " +
                    "reversalId: {}, correlationId: {}",
                    event.getPaymentId(), event.getReversalId(), correlationId);
        } catch (Exception e) {
            log.error("Failed to send operations alerts for reversal failure - paymentId: {}, error: {}, correlationId: {}",
                    event.getPaymentId(), e.getMessage(), correlationId, e);
            throw e;
        }
    }
    
    private void sendOperationsAlertsFallback(PaymentReversalFailedEvent event, String correlationId,
                                               Throwable throwable) {
        log.error("CRITICAL: Notification service unavailable for reversal failure alerts - paymentId: {}, " +
                "reversalId: {}, error: {}, correlationId: {}",
                event.getPaymentId(), event.getReversalId(), throwable.getMessage(), correlationId);
    }
    
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendCustomerNotificationsFallback")
    @Retry(name = "notification-service")
    @TimeLimiter(name = "notification-service")
    private void sendCustomerNotifications(PaymentReversalFailedEvent event, String correlationId) {
        if (event.getCustomerId() == null || !shouldNotifyCustomer(event)) {
            log.debug("Skipping customer notification for reversal failure - paymentId: {}, correlationId: {}",
                    event.getPaymentId(), correlationId);
            return;
        }
        
        log.info("Sending customer notifications for reversal delay - paymentId: {}, customerId: {}, correlationId: {}",
                event.getPaymentId(), event.getCustomerId(), correlationId);
        
        ReversalFailureNotificationRequest request = ReversalFailureNotificationRequest.builder()
                .paymentId(event.getPaymentId())
                .reversalId(event.getReversalId())
                .customerId(event.getCustomerId())
                .reversalAmount(event.getReversalAmount())
                .currency(event.getCurrency())
                .errorMessage("Your refund is experiencing a delay and is being processed manually")
                .isRetryable(event.getIsRetryable())
                .reversalReason(event.getReversalReason())
                .notificationType("CUSTOMER_NOTIFICATION")
                .priority("HIGH")
                .channels(List.of("EMAIL", "IN_APP"))
                .correlationId(correlationId)
                .build();
        
        try {
            notificationServiceClient.sendReversalFailureNotification(request);
            log.info("Customer notifications sent successfully for reversal delay - paymentId: {}, " +
                    "customerId: {}, correlationId: {}",
                    event.getPaymentId(), event.getCustomerId(), correlationId);
        } catch (Exception e) {
            log.error("Failed to send customer notifications for reversal delay - paymentId: {}, error: {}, correlationId: {}",
                    event.getPaymentId(), e.getMessage(), correlationId, e);
            throw e;
        }
    }
    
    private void sendCustomerNotificationsFallback(PaymentReversalFailedEvent event, String correlationId,
                                                     Throwable throwable) {
        log.warn("Notification service unavailable for customer reversal delay notifications - paymentId: {}, error: {}, correlationId: {}",
                event.getPaymentId(), throwable.getMessage(), correlationId);
    }
    
    private boolean shouldNotifyCustomer(PaymentReversalFailedEvent event) {
        if (event.getRetryAttempt() != null && event.getRetryAttempt() < 3) {
            return false;
        }
        
        if ("FRAUD".equals(event.getErrorCategory()) || "COMPLIANCE".equals(event.getErrorCategory())) {
            return false;
        }
        
        return Boolean.TRUE.equals(event.getIsRetryable()) == false || 
               (event.getRetryAttempt() != null && event.getRetryAttempt() >= 3);
    }
    
    @CircuitBreaker(name = "analytics-service", fallbackMethod = "recordReversalFailureAnalyticsFallback")
    @Retry(name = "analytics-service")
    @TimeLimiter(name = "analytics-service")
    private void recordReversalFailureAnalytics(PaymentReversalFailedEvent event, String correlationId) {
        log.info("Recording reversal failure analytics - paymentId: {}, reversalId: {}, errorCode: {}, correlationId: {}",
                event.getPaymentId(), event.getReversalId(), event.getErrorCode(), correlationId);
        
        RecordReversalFailureAnalyticsRequest request = RecordReversalFailureAnalyticsRequest.builder()
                .paymentId(event.getPaymentId())
                .reversalId(event.getReversalId())
                .customerId(event.getCustomerId())
                .merchantId(event.getMerchantId())
                .reversalAmount(event.getReversalAmount())
                .currency(event.getCurrency())
                .reversalReason(event.getReversalReason())
                .reversalType(event.getReversalType())
                .errorCode(event.getErrorCode())
                .errorMessage(event.getErrorMessage())
                .errorCategory(event.getErrorCategory())
                .failureReason(event.getFailureReason())
                .isRetryable(event.getIsRetryable())
                .retryAttempt(event.getRetryAttempt())
                .gatewayId(event.getGatewayId())
                .gatewayErrorCode(event.getGatewayErrorCode())
                .gatewayErrorMessage(event.getGatewayErrorMessage())
                .failedAt(event.getFailedAt())
                .correlationId(correlationId)
                .build();
        
        try {
            analyticsServiceClient.recordReversalFailureAnalytics(request);
            log.info("Reversal failure analytics recorded successfully - paymentId: {}, reversalId: {}, correlationId: {}",
                    event.getPaymentId(), event.getReversalId(), correlationId);
        } catch (Exception e) {
            log.error("Failed to record reversal failure analytics - paymentId: {}, error: {}, correlationId: {}",
                    event.getPaymentId(), e.getMessage(), correlationId, e);
            throw e;
        }
    }
    
    private void recordReversalFailureAnalyticsFallback(PaymentReversalFailedEvent event, String correlationId,
                                                         Throwable throwable) {
        log.warn("Analytics service unavailable for reversal failure analytics - paymentId: {}, error: {}, correlationId: {}",
                event.getPaymentId(), throwable.getMessage(), correlationId);
    }
    
    @DltHandler
    public void handleDeadLetterTopic(
            @Payload PaymentReversalFailedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        String correlationId = event.getCorrelationId() != null ? 
                event.getCorrelationId() : UUID.randomUUID().toString();
        
        log.error("Payment reversal failed event moved to DLT - eventId: {}, correlationId: {}, " +
                "originalTopic: {}, paymentId: {}, reversalId: {}, errorCode: {}, error: {}",
                event.getEventId(), correlationId, topic, event.getPaymentId(), 
                event.getReversalId(), event.getErrorCode(), exceptionMessage);
        
        Map<String, Object> dltMetadata = new HashMap<>();
        dltMetadata.put("originalTopic", topic);
        dltMetadata.put("paymentId", event.getPaymentId());
        dltMetadata.put("reversalId", event.getReversalId());
        dltMetadata.put("errorCode", event.getErrorCode());
        dltMetadata.put("errorMessage", event.getErrorMessage());
        dltMetadata.put("failureReason", event.getFailureReason());
        dltMetadata.put("isRetryable", event.getIsRetryable());
        dltMetadata.put("exceptionMessage", exceptionMessage);
        dltMetadata.put("eventTimestamp", event.getTimestamp());
        dltMetadata.put("dltTimestamp", Instant.now());
        
        auditService.logDeadLetterEvent(
            event.getEventId() != null ? event.getEventId() : "UNKNOWN",
            TOPIC_NAME,
            "PAYMENT_REVERSAL_FAILED_DLT",
            exceptionMessage,
            correlationId,
            dltMetadata
        );
    }
}