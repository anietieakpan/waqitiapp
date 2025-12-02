package com.waqiti.payment.events.consumers;

import com.waqiti.payment.client.AnalyticsServiceClient;
import com.waqiti.payment.client.LedgerServiceClient;
import com.waqiti.payment.client.NotificationServiceClient;
import com.waqiti.payment.dto.analytics.RecordDisputeAnalyticsRequest;
import com.waqiti.payment.dto.ledger.RecordDisputeResolutionRequest;
import com.waqiti.payment.dto.ledger.RecordDisputeResolutionResponse;
import com.waqiti.payment.dto.notification.DisputeResolutionNotificationRequest;
import com.waqiti.payment.events.PaymentDisputeProcessedEvent;
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
 * Payment Dispute Processed Event Consumer
 * 
 * CRITICAL CONSUMER - Processes payment dispute resolution events
 * 
 * EVENT SOURCE:
 * - PaymentDisputeConsumer: Line 633 publishes when dispute is processed/resolved
 * 
 * BUSINESS CRITICALITY:
 * - Records dispute resolutions for compliance and reporting
 * - Maintains audit trail of dispute handling
 * - Tracks chargeback metrics and trends
 * - Notifies customers and merchants of dispute outcomes
 * - Supports analytics on dispute patterns
 * 
 * DISPUTE RESOLUTION TYPES:
 * - Customer favor: Full or partial refund issued
 * - Merchant favor: Dispute rejected, payment stands
 * - Investigation required: Escalated for manual review
 * - Fraud detected: Account actions triggered
 * - Automatic resolution: Based on evidence and rules
 * 
 * PROCESSING ACTIONS:
 * - Record dispute resolution in ledger for financial tracking
 * - Send multi-channel notifications to customers and merchants
 * - Capture dispute analytics for chargeback prevention
 * - Audit all dispute decisions for compliance
 * - Track resolution metrics (time, outcome, reasons)
 * 
 * BUSINESS VALUE:
 * - Chargeback prevention: Track patterns to prevent future disputes
 * - Customer trust: Transparent dispute resolution process
 * - Merchant protection: Fair dispute evaluation
 * - Compliance: Complete audit trail for regulatory requirements
 * - Analytics: Identify root causes and improvement opportunities
 * 
 * FAILURE IMPACT:
 * - Loss of dispute resolution tracking
 * - Missing notifications to affected parties
 * - Incomplete financial records
 * - Compliance gaps in dispute documentation
 * - Reduced ability to prevent future disputes
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
public class PaymentDisputeProcessedEventConsumer {
    
    private final LedgerServiceClient ledgerServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final AnalyticsServiceClient analyticsServiceClient;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    
    private static final String TOPIC_NAME = "payment-dispute-processed";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    
    @KafkaListener(
        topics = "${kafka.topics.payment-dispute-processed:payment-dispute-processed}",
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
    public void handlePaymentDisputeProcessed(
            @Payload PaymentDisputeProcessedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            Acknowledgment acknowledgment) {
        
        long startTime = System.currentTimeMillis();
        String correlationId = event.getCorrelationId() != null ? 
                event.getCorrelationId() : UUID.randomUUID().toString();
        
        log.info("Received payment dispute processed event - eventId: {}, correlationId: {}, " +
                "topic: {}, partition: {}, offset: {}, disputeId: {}, paymentId: {}, " +
                "decision: {}, amount: {}", 
                event.getEventId(), correlationId, topic, partition, offset, event.getDisputeId(),
                event.getPaymentId(), event.getDecision(), event.getAmount());
        
        try {
            if (event.getDisputeId() == null || event.getPaymentId() == null) {
                log.error("Invalid payment dispute processed event - missing required fields: disputeId={}, paymentId={}",
                        event.getDisputeId(), event.getPaymentId());
                auditService.logEventProcessingFailure(
                    event.getEventId() != null ? event.getEventId() : "UNKNOWN", 
                    TOPIC_NAME, 
                    "VALIDATION_FAILED",
                    "Missing required fields: disputeId or paymentId",
                    correlationId,
                    Map.of("event", event)
                );
                acknowledgment.acknowledge();
                return;
            }
            
            String eventId = event.getEventId() != null ? event.getEventId() : 
                    UUID.randomUUID().toString();
            
            if (idempotencyService.isDuplicate(eventId, IDEMPOTENCY_TTL)) {
                log.warn("Duplicate payment dispute processed event detected - eventId: {}, correlationId: {}", 
                        eventId, correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            processDisputeResolution(event, correlationId);
            
            idempotencyService.recordProcessedEvent(eventId, IDEMPOTENCY_TTL);
            
            long processingTime = System.currentTimeMillis() - startTime;
            log.info("Successfully processed payment dispute processed event - eventId: {}, correlationId: {}, " +
                    "disputeId: {}, paymentId: {}, decision: {}, processingTimeMs: {}", 
                    eventId, correlationId, event.getDisputeId(), event.getPaymentId(),
                    event.getDecision(), processingTime);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("Failed to process payment dispute processed event - eventId: {}, correlationId: {}, " +
                    "disputeId: {}, error: {}, processingTimeMs: {}", 
                    event.getEventId(), correlationId, event.getDisputeId(), 
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
                "Failed to process payment dispute processed event: " + e.getMessage(), e);
        }
    }
    
    private void processDisputeResolution(PaymentDisputeProcessedEvent event, String correlationId) {
        long startTime = System.currentTimeMillis();
        
        log.info("Processing payment dispute resolution - disputeId: {}, paymentId: {}, decision: {}, " +
                "resolutionType: {}, refundAmount: {}, correlationId: {}",
                event.getDisputeId(), event.getPaymentId(), event.getDecision(),
                event.getResolutionType(), event.getRefundAmount(), correlationId);
        
        recordDisputeResolutionInLedger(event, correlationId);
        
        sendDisputeResolutionNotifications(event, correlationId);
        
        recordDisputeAnalytics(event, correlationId);
        
        long processingTime = System.currentTimeMillis() - startTime;
        
        Map<String, Object> auditMetadata = new HashMap<>();
        auditMetadata.put("disputeId", event.getDisputeId());
        auditMetadata.put("paymentId", event.getPaymentId());
        auditMetadata.put("decision", event.getDecision());
        auditMetadata.put("resolutionType", event.getResolutionType());
        auditMetadata.put("refundAmount", event.getRefundAmount());
        auditMetadata.put("fundsHeld", event.getFundsHeld());
        auditMetadata.put("accountFrozen", event.getAccountFrozen());
        auditMetadata.put("disputeReason", event.getDisputeReason());
        auditMetadata.put("approvedBy", event.getApprovedBy());
        if (event.getMetadata() != null) {
            auditMetadata.putAll(event.getMetadata());
        }
        
        auditService.logPaymentDisputeProcessed(
            event.getEventId() != null ? event.getEventId() : UUID.randomUUID().toString(),
            event.getDisputeId(),
            event.getPaymentId(),
            event.getDecision(),
            event.getResolutionType(),
            event.getRefundAmount(),
            event.getDisputeReason(),
            event.getApprovedBy(),
            processingTime,
            correlationId,
            auditMetadata
        );
        
        log.info("Payment dispute resolution processed successfully - disputeId: {}, paymentId: {}, " +
                "decision: {}, processingTimeMs: {}, correlationId: {}",
                event.getDisputeId(), event.getPaymentId(), event.getDecision(), 
                processingTime, correlationId);
    }
    
    @CircuitBreaker(name = "ledger-service", fallbackMethod = "recordDisputeResolutionInLedgerFallback")
    @Retry(name = "ledger-service")
    @TimeLimiter(name = "ledger-service")
    private void recordDisputeResolutionInLedger(PaymentDisputeProcessedEvent event, String correlationId) {
        log.info("Recording dispute resolution in ledger - disputeId: {}, decision: {}, refundAmount: {}, correlationId: {}",
                event.getDisputeId(), event.getDecision(), event.getRefundAmount(), correlationId);
        
        RecordDisputeResolutionRequest request = RecordDisputeResolutionRequest.builder()
                .disputeId(event.getDisputeId())
                .paymentId(event.getPaymentId())
                .customerId(event.getCustomerId())
                .merchantId(event.getMerchantId())
                .decision(event.getDecision())
                .resolutionType(event.getResolutionType())
                .amount(event.getAmount())
                .refundAmount(event.getRefundAmount())
                .currency(event.getCurrency())
                .disputeReason(event.getDisputeReason())
                .investigationResult(event.getInvestigationResult())
                .approvedBy(event.getApprovedBy())
                .resolutionTimestamp(event.getTimestamp())
                .correlationId(correlationId)
                .build();
        
        try {
            RecordDisputeResolutionResponse response = ledgerServiceClient.recordDisputeResolution(request);
            log.info("Dispute resolution recorded in ledger successfully - disputeId: {}, ledgerEntryId: {}, correlationId: {}",
                    event.getDisputeId(), response.getLedgerEntryId(), correlationId);
        } catch (Exception e) {
            log.error("Failed to record dispute resolution in ledger - disputeId: {}, error: {}, correlationId: {}",
                    event.getDisputeId(), e.getMessage(), correlationId, e);
            throw e;
        }
    }
    
    private void recordDisputeResolutionInLedgerFallback(PaymentDisputeProcessedEvent event, String correlationId,
                                                          Throwable throwable) {
        log.error("CRITICAL: Ledger service unavailable for dispute resolution recording - disputeId: {}, " +
                "paymentId: {}, decision: {}, error: {}, correlationId: {}",
                event.getDisputeId(), event.getPaymentId(), event.getDecision(), 
                throwable.getMessage(), correlationId);
        
        auditService.logCriticalLedgerFailure(
            "RECORD_DISPUTE_RESOLUTION",
            event.getCustomerId(),
            event.getPaymentId(),
            throwable.getMessage(),
            correlationId,
            Map.of(
                "disputeId", event.getDisputeId(),
                "decision", event.getDecision(),
                "refundAmount", event.getRefundAmount() != null ? event.getRefundAmount() : "NULL"
            )
        );
        
        throw new ServiceIntegrationException(
            "Critical ledger service failure for dispute resolution: " + throwable.getMessage(), throwable);
    }
    
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendDisputeResolutionNotificationsFallback")
    @Retry(name = "notification-service")
    @TimeLimiter(name = "notification-service")
    private void sendDisputeResolutionNotifications(PaymentDisputeProcessedEvent event, String correlationId) {
        log.info("Sending dispute resolution notifications - disputeId: {}, customerId: {}, " +
                "merchantId: {}, correlationId: {}",
                event.getDisputeId(), event.getCustomerId(), event.getMerchantId(), correlationId);
        
        DisputeResolutionNotificationRequest request = DisputeResolutionNotificationRequest.builder()
                .userId(event.getCustomerId())
                .merchantId(event.getMerchantId())
                .disputeId(event.getDisputeId())
                .paymentId(event.getPaymentId())
                .decision(event.getDecision())
                .resolutionType(event.getResolutionType())
                .amount(event.getAmount())
                .refundAmount(event.getRefundAmount())
                .currency(event.getCurrency())
                .disputeReason(event.getDisputeReason())
                .channels(List.of("EMAIL", "PUSH", "IN_APP"))
                .correlationId(correlationId)
                .build();
        
        try {
            notificationServiceClient.sendDisputeResolutionNotification(request);
            log.info("Dispute resolution notifications sent successfully - disputeId: {}, correlationId: {}",
                    event.getDisputeId(), correlationId);
        } catch (Exception e) {
            log.error("Failed to send dispute resolution notifications - disputeId: {}, error: {}, correlationId: {}",
                    event.getDisputeId(), e.getMessage(), correlationId, e);
            throw e;
        }
    }
    
    private void sendDisputeResolutionNotificationsFallback(PaymentDisputeProcessedEvent event, String correlationId,
                                                             Throwable throwable) {
        log.warn("Notification service unavailable for dispute resolution notifications - disputeId: {}, error: {}, correlationId: {}",
                event.getDisputeId(), throwable.getMessage(), correlationId);
    }
    
    @CircuitBreaker(name = "analytics-service", fallbackMethod = "recordDisputeAnalyticsFallback")
    @Retry(name = "analytics-service")
    @TimeLimiter(name = "analytics-service")
    private void recordDisputeAnalytics(PaymentDisputeProcessedEvent event, String correlationId) {
        log.info("Recording dispute analytics - disputeId: {}, decision: {}, correlationId: {}",
                event.getDisputeId(), event.getDecision(), correlationId);
        
        RecordDisputeAnalyticsRequest request = RecordDisputeAnalyticsRequest.builder()
                .disputeId(event.getDisputeId())
                .paymentId(event.getPaymentId())
                .customerId(event.getCustomerId())
                .merchantId(event.getMerchantId())
                .decision(event.getDecision())
                .resolutionType(event.getResolutionType())
                .amount(event.getAmount())
                .refundAmount(event.getRefundAmount())
                .currency(event.getCurrency())
                .type(event.getType())
                .disputeReason(event.getDisputeReason())
                .investigationResult(event.getInvestigationResult())
                .fundsHeld(event.getFundsHeld())
                .accountFrozen(event.getAccountFrozen())
                .resolutionTimestamp(event.getTimestamp())
                .correlationId(correlationId)
                .build();
        
        try {
            analyticsServiceClient.recordDisputeAnalytics(request);
            log.info("Dispute analytics recorded successfully - disputeId: {}, correlationId: {}",
                    event.getDisputeId(), correlationId);
        } catch (Exception e) {
            log.error("Failed to record dispute analytics - disputeId: {}, error: {}, correlationId: {}",
                    event.getDisputeId(), e.getMessage(), correlationId, e);
            throw e;
        }
    }
    
    private void recordDisputeAnalyticsFallback(PaymentDisputeProcessedEvent event, String correlationId,
                                                 Throwable throwable) {
        log.warn("Analytics service unavailable for dispute analytics - disputeId: {}, error: {}, correlationId: {}",
                event.getDisputeId(), throwable.getMessage(), correlationId);
    }
    
    @DltHandler
    public void handleDeadLetterTopic(
            @Payload PaymentDisputeProcessedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        String correlationId = event.getCorrelationId() != null ? 
                event.getCorrelationId() : UUID.randomUUID().toString();
        
        log.error("Payment dispute processed event moved to DLT - eventId: {}, correlationId: {}, " +
                "originalTopic: {}, disputeId: {}, paymentId: {}, decision: {}, error: {}",
                event.getEventId(), correlationId, topic, event.getDisputeId(), 
                event.getPaymentId(), event.getDecision(), exceptionMessage);
        
        Map<String, Object> dltMetadata = new HashMap<>();
        dltMetadata.put("originalTopic", topic);
        dltMetadata.put("disputeId", event.getDisputeId());
        dltMetadata.put("paymentId", event.getPaymentId());
        dltMetadata.put("decision", event.getDecision());
        dltMetadata.put("resolutionType", event.getResolutionType());
        dltMetadata.put("refundAmount", event.getRefundAmount());
        dltMetadata.put("exceptionMessage", exceptionMessage);
        dltMetadata.put("eventTimestamp", event.getTimestamp());
        dltMetadata.put("dltTimestamp", Instant.now());
        
        auditService.logDeadLetterEvent(
            event.getEventId() != null ? event.getEventId() : "UNKNOWN",
            TOPIC_NAME,
            "PAYMENT_DISPUTE_PROCESSED_DLT",
            exceptionMessage,
            correlationId,
            dltMetadata
        );
    }
}