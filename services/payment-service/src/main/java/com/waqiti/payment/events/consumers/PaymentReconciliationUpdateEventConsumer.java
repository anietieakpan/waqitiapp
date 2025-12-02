package com.waqiti.payment.events.consumers;

import com.waqiti.payment.client.AnalyticsServiceClient;
import com.waqiti.payment.client.LedgerServiceClient;
import com.waqiti.payment.client.NotificationServiceClient;
import com.waqiti.payment.dto.analytics.RecordReconciliationAnalyticsRequest;
import com.waqiti.payment.dto.ledger.RecordReconciliationUpdateRequest;
import com.waqiti.payment.dto.ledger.RecordReconciliationUpdateResponse;
import com.waqiti.payment.dto.notification.ReconciliationStatusNotificationRequest;
import com.waqiti.payment.events.PaymentReconciliationUpdateEvent;
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
 * Payment Reconciliation Update Event Consumer
 * 
 * CRITICAL CONSUMER - Processes payment reconciliation status update events
 * 
 * EVENT SOURCE:
 * - PaymentService.updateReconciliationStatus(): Line 1010 publishes reconciliation updates
 * 
 * BUSINESS CRITICALITY:
 * - Maintains financial accuracy through reconciliation tracking
 * - Ensures all payments are properly reconciled
 * - Supports compliance with financial regulations
 * - Enables real-time reconciliation monitoring
 * - Tracks settlement and payment matching
 * 
 * RECONCILIATION STATUS TYPES:
 * - INITIATED: Reconciliation process started
 * - IN_PROGRESS: Currently reconciling transactions
 * - MATCHED: All transactions reconciled successfully
 * - DISCREPANCIES: Found unmatched or disputed items
 * - COMPLETED: Reconciliation finished successfully
 * - FAILED: Reconciliation process failed
 * - CANCELLED: Reconciliation cancelled
 * 
 * PROCESSING ACTIONS:
 * - Record reconciliation status in ledger for audit trail
 * - Send alerts for discrepancies or failures
 * - Notify finance teams of reconciliation completion
 * - Capture reconciliation analytics for reporting
 * - Audit all reconciliation state changes
 * - Track reconciliation SLAs and performance
 * 
 * BUSINESS VALUE:
 * - Financial integrity: Complete transaction reconciliation
 * - Compliance: Regulatory audit trail
 * - Operations: Real-time reconciliation monitoring
 * - Risk management: Early discrepancy detection
 * - Analytics: Reconciliation patterns and performance
 * 
 * FAILURE IMPACT:
 * - Loss of reconciliation visibility
 * - Missing discrepancy alerts
 * - Incomplete financial records
 * - Compliance violations
 * - Delayed detection of financial discrepancies
 * 
 * RESILIENCE:
 * - Idempotency with 24-hour TTL
 * - Circuit breakers on all external calls
 * - Critical ledger recording (hard fail)
 * - High-priority discrepancy alerting
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
public class PaymentReconciliationUpdateEventConsumer {
    
    private final LedgerServiceClient ledgerServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final AnalyticsServiceClient analyticsServiceClient;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    
    private static final String TOPIC_NAME = "payment-reconciliation-updates";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    
    @KafkaListener(
        topics = "${kafka.topics.payment-reconciliation-updates:payment-reconciliation-updates}",
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
    public void handlePaymentReconciliationUpdate(
            @Payload PaymentReconciliationUpdateEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            Acknowledgment acknowledgment) {
        
        long startTime = System.currentTimeMillis();
        String correlationId = event.getCorrelationId() != null ? 
                event.getCorrelationId() : UUID.randomUUID().toString();
        
        log.info("Received payment reconciliation update event - eventId: {}, correlationId: {}, " +
                "topic: {}, partition: {}, offset: {}, reconciliationId: {}, settlementId: {}, " +
                "status: {}, discrepancyAmount: {}", 
                event.getEventId(), correlationId, topic, partition, offset, event.getReconciliationId(),
                event.getSettlementId(), event.getStatus(), event.getDiscrepancyAmount());
        
        try {
            if (event.getReconciliationId() == null) {
                log.error("Invalid payment reconciliation update event - missing reconciliationId");
                auditService.logEventProcessingFailure(
                    event.getEventId() != null ? event.getEventId() : "UNKNOWN", 
                    TOPIC_NAME, 
                    "VALIDATION_FAILED",
                    "Missing required field: reconciliationId",
                    correlationId,
                    Map.of("event", event)
                );
                acknowledgment.acknowledge();
                return;
            }
            
            String eventId = event.getEventId() != null ? event.getEventId() : 
                    UUID.randomUUID().toString();
            
            if (idempotencyService.isDuplicate(eventId, IDEMPOTENCY_TTL)) {
                log.warn("Duplicate payment reconciliation update event detected - eventId: {}, correlationId: {}", 
                        eventId, correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            processReconciliationUpdate(event, correlationId);
            
            idempotencyService.recordProcessedEvent(eventId, IDEMPOTENCY_TTL);
            
            long processingTime = System.currentTimeMillis() - startTime;
            log.info("Successfully processed payment reconciliation update event - eventId: {}, correlationId: {}, " +
                    "reconciliationId: {}, status: {}, processingTimeMs: {}", 
                    eventId, correlationId, event.getReconciliationId(), event.getStatus(), processingTime);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("Failed to process payment reconciliation update event - eventId: {}, correlationId: {}, " +
                    "reconciliationId: {}, error: {}, processingTimeMs: {}", 
                    event.getEventId(), correlationId, event.getReconciliationId(), 
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
                "Failed to process payment reconciliation update event: " + e.getMessage(), e);
        }
    }
    
    private void processReconciliationUpdate(PaymentReconciliationUpdateEvent event, String correlationId) {
        long startTime = System.currentTimeMillis();
        
        log.info("Processing payment reconciliation update - reconciliationId: {}, settlementId: {}, " +
                "status: {}, matchedTransactions: {}, discrepancyAmount: {}, correlationId: {}",
                event.getReconciliationId(), event.getSettlementId(), event.getStatus(),
                event.getMatchedTransactions(), event.getDiscrepancyAmount(), correlationId);
        
        recordReconciliationUpdateInLedger(event, correlationId);
        
        sendReconciliationNotifications(event, correlationId);
        
        recordReconciliationAnalytics(event, correlationId);
        
        long processingTime = System.currentTimeMillis() - startTime;
        
        Map<String, Object> auditMetadata = new HashMap<>();
        auditMetadata.put("reconciliationId", event.getReconciliationId());
        auditMetadata.put("settlementId", event.getSettlementId());
        auditMetadata.put("status", event.getStatus());
        auditMetadata.put("previousStatus", event.getPreviousStatus());
        auditMetadata.put("reconciliationType", event.getReconciliationType());
        auditMetadata.put("reconciliationAmount", event.getReconciliationAmount());
        auditMetadata.put("matchedTransactions", event.getMatchedTransactions());
        auditMetadata.put("unmatchedTransactions", event.getUnmatchedTransactions());
        auditMetadata.put("discrepancyAmount", event.getDiscrepancyAmount());
        auditMetadata.put("discrepancyReason", event.getDiscrepancyReason());
        auditMetadata.put("initiatedBy", event.getInitiatedBy());
        auditMetadata.put("approvedBy", event.getApprovedBy());
        if (event.getMetadata() != null) {
            auditMetadata.putAll(event.getMetadata());
        }
        
        auditService.logPaymentReconciliationUpdate(
            event.getEventId() != null ? event.getEventId() : UUID.randomUUID().toString(),
            event.getReconciliationId(),
            event.getSettlementId(),
            event.getStatus(),
            event.getPreviousStatus(),
            event.getReconciliationAmount(),
            event.getDiscrepancyAmount(),
            event.getMatchedTransactions(),
            event.getUnmatchedTransactions(),
            processingTime,
            correlationId,
            auditMetadata
        );
        
        log.info("Payment reconciliation update processed successfully - reconciliationId: {}, status: {}, " +
                "processingTimeMs: {}, correlationId: {}",
                event.getReconciliationId(), event.getStatus(), processingTime, correlationId);
    }
    
    @CircuitBreaker(name = "ledger-service", fallbackMethod = "recordReconciliationUpdateInLedgerFallback")
    @Retry(name = "ledger-service")
    @TimeLimiter(name = "ledger-service")
    private void recordReconciliationUpdateInLedger(PaymentReconciliationUpdateEvent event, String correlationId) {
        log.info("Recording reconciliation update in ledger - reconciliationId: {}, status: {}, " +
                "discrepancyAmount: {}, correlationId: {}",
                event.getReconciliationId(), event.getStatus(), event.getDiscrepancyAmount(), correlationId);
        
        RecordReconciliationUpdateRequest request = RecordReconciliationUpdateRequest.builder()
                .reconciliationId(event.getReconciliationId())
                .settlementId(event.getSettlementId())
                .paymentId(event.getPaymentId())
                .status(event.getStatus())
                .previousStatus(event.getPreviousStatus())
                .reconciliationType(event.getReconciliationType())
                .reconciliationAmount(event.getReconciliationAmount())
                .currency(event.getCurrency())
                .matchedTransactions(event.getMatchedTransactions())
                .unmatchedTransactions(event.getUnmatchedTransactions())
                .discrepancyAmount(event.getDiscrepancyAmount())
                .discrepancyReason(event.getDiscrepancyReason())
                .gatewayId(event.getGatewayId())
                .merchantId(event.getMerchantId())
                .periodStartDate(event.getPeriodStartDate())
                .periodEndDate(event.getPeriodEndDate())
                .completedAt(event.getCompletedAt())
                .updatedAt(event.getUpdatedAt())
                .correlationId(correlationId)
                .build();
        
        try {
            RecordReconciliationUpdateResponse response = ledgerServiceClient.recordReconciliationUpdate(request);
            log.info("Reconciliation update recorded in ledger successfully - reconciliationId: {}, " +
                    "ledgerEntryId: {}, correlationId: {}",
                    event.getReconciliationId(), response.getLedgerEntryId(), correlationId);
        } catch (Exception e) {
            log.error("Failed to record reconciliation update in ledger - reconciliationId: {}, error: {}, correlationId: {}",
                    event.getReconciliationId(), e.getMessage(), correlationId, e);
            throw e;
        }
    }
    
    private void recordReconciliationUpdateInLedgerFallback(PaymentReconciliationUpdateEvent event, String correlationId,
                                                             Throwable throwable) {
        log.error("CRITICAL: Ledger service unavailable for reconciliation update recording - reconciliationId: {}, " +
                "status: {}, discrepancyAmount: {}, error: {}, correlationId: {}",
                event.getReconciliationId(), event.getStatus(), event.getDiscrepancyAmount(), 
                throwable.getMessage(), correlationId);
        
        auditService.logCriticalLedgerFailure(
            "RECORD_RECONCILIATION_UPDATE",
            event.getInitiatedBy(),
            event.getReconciliationId(),
            throwable.getMessage(),
            correlationId,
            Map.of(
                "reconciliationId", event.getReconciliationId(),
                "status", event.getStatus(),
                "discrepancyAmount", event.getDiscrepancyAmount() != null ? event.getDiscrepancyAmount() : "NULL"
            )
        );
        
        throw new ServiceIntegrationException(
            "Critical ledger service failure for reconciliation update: " + throwable.getMessage(), throwable);
    }
    
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendReconciliationNotificationsFallback")
    @Retry(name = "notification-service")
    @TimeLimiter(name = "notification-service")
    private void sendReconciliationNotifications(PaymentReconciliationUpdateEvent event, String correlationId) {
        if (!shouldSendNotification(event)) {
            log.debug("Skipping notification for reconciliation update - reconciliationId: {}, status: {}, correlationId: {}",
                    event.getReconciliationId(), event.getStatus(), correlationId);
            return;
        }
        
        log.info("Sending reconciliation status notifications - reconciliationId: {}, status: {}, correlationId: {}",
                event.getReconciliationId(), event.getStatus(), correlationId);
        
        ReconciliationStatusNotificationRequest request = ReconciliationStatusNotificationRequest.builder()
                .reconciliationId(event.getReconciliationId())
                .settlementId(event.getSettlementId())
                .status(event.getStatus())
                .reconciliationType(event.getReconciliationType())
                .reconciliationAmount(event.getReconciliationAmount())
                .currency(event.getCurrency())
                .matchedTransactions(event.getMatchedTransactions())
                .unmatchedTransactions(event.getUnmatchedTransactions())
                .discrepancyAmount(event.getDiscrepancyAmount())
                .discrepancyReason(event.getDiscrepancyReason())
                .gatewayId(event.getGatewayId())
                .merchantId(event.getMerchantId())
                .completedAt(event.getCompletedAt())
                .priority(determinePriority(event))
                .channels(determineChannels(event))
                .correlationId(correlationId)
                .build();
        
        try {
            notificationServiceClient.sendReconciliationStatusNotification(request);
            log.info("Reconciliation notifications sent successfully - reconciliationId: {}, status: {}, correlationId: {}",
                    event.getReconciliationId(), event.getStatus(), correlationId);
        } catch (Exception e) {
            log.error("Failed to send reconciliation notifications - reconciliationId: {}, error: {}, correlationId: {}",
                    event.getReconciliationId(), e.getMessage(), correlationId, e);
            throw e;
        }
    }
    
    private void sendReconciliationNotificationsFallback(PaymentReconciliationUpdateEvent event, String correlationId,
                                                          Throwable throwable) {
        log.warn("Notification service unavailable for reconciliation notifications - reconciliationId: {}, error: {}, correlationId: {}",
                event.getReconciliationId(), throwable.getMessage(), correlationId);
    }
    
    private boolean shouldSendNotification(PaymentReconciliationUpdateEvent event) {
        String status = event.getStatus();
        return "COMPLETED".equals(status) || 
               "DISCREPANCIES".equals(status) || 
               "FAILED".equals(status) ||
               (event.getDiscrepancyAmount() != null && event.getDiscrepancyAmount().compareTo(java.math.BigDecimal.ZERO) > 0);
    }
    
    private String determinePriority(PaymentReconciliationUpdateEvent event) {
        if ("FAILED".equals(event.getStatus()) || 
            (event.getDiscrepancyAmount() != null && event.getDiscrepancyAmount().compareTo(java.math.BigDecimal.valueOf(1000)) > 0)) {
            return "CRITICAL";
        } else if ("DISCREPANCIES".equals(event.getStatus()) || 
                   (event.getDiscrepancyAmount() != null && event.getDiscrepancyAmount().compareTo(java.math.BigDecimal.ZERO) > 0)) {
            return "HIGH";
        }
        return "MEDIUM";
    }
    
    private List<String> determineChannels(PaymentReconciliationUpdateEvent event) {
        if ("FAILED".equals(event.getStatus()) || "DISCREPANCIES".equals(event.getStatus())) {
            return List.of("EMAIL", "SLACK", "IN_APP");
        }
        return List.of("EMAIL", "IN_APP");
    }
    
    @CircuitBreaker(name = "analytics-service", fallbackMethod = "recordReconciliationAnalyticsFallback")
    @Retry(name = "analytics-service")
    @TimeLimiter(name = "analytics-service")
    private void recordReconciliationAnalytics(PaymentReconciliationUpdateEvent event, String correlationId) {
        log.info("Recording reconciliation analytics - reconciliationId: {}, status: {}, correlationId: {}",
                event.getReconciliationId(), event.getStatus(), correlationId);
        
        RecordReconciliationAnalyticsRequest request = RecordReconciliationAnalyticsRequest.builder()
                .reconciliationId(event.getReconciliationId())
                .settlementId(event.getSettlementId())
                .paymentId(event.getPaymentId())
                .status(event.getStatus())
                .previousStatus(event.getPreviousStatus())
                .reconciliationType(event.getReconciliationType())
                .reconciliationAmount(event.getReconciliationAmount())
                .currency(event.getCurrency())
                .matchedTransactions(event.getMatchedTransactions())
                .unmatchedTransactions(event.getUnmatchedTransactions())
                .discrepancyAmount(event.getDiscrepancyAmount())
                .discrepancyReason(event.getDiscrepancyReason())
                .gatewayId(event.getGatewayId())
                .merchantId(event.getMerchantId())
                .periodStartDate(event.getPeriodStartDate())
                .periodEndDate(event.getPeriodEndDate())
                .initiatedAt(event.getInitiatedAt())
                .completedAt(event.getCompletedAt())
                .correlationId(correlationId)
                .build();
        
        try {
            analyticsServiceClient.recordReconciliationAnalytics(request);
            log.info("Reconciliation analytics recorded successfully - reconciliationId: {}, status: {}, correlationId: {}",
                    event.getReconciliationId(), event.getStatus(), correlationId);
        } catch (Exception e) {
            log.error("Failed to record reconciliation analytics - reconciliationId: {}, error: {}, correlationId: {}",
                    event.getReconciliationId(), e.getMessage(), correlationId, e);
            throw e;
        }
    }
    
    private void recordReconciliationAnalyticsFallback(PaymentReconciliationUpdateEvent event, String correlationId,
                                                        Throwable throwable) {
        log.warn("Analytics service unavailable for reconciliation analytics - reconciliationId: {}, error: {}, correlationId: {}",
                event.getReconciliationId(), throwable.getMessage(), correlationId);
    }
    
    @DltHandler
    public void handleDeadLetterTopic(
            @Payload PaymentReconciliationUpdateEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        String correlationId = event.getCorrelationId() != null ? 
                event.getCorrelationId() : UUID.randomUUID().toString();
        
        log.error("Payment reconciliation update event moved to DLT - eventId: {}, correlationId: {}, " +
                "originalTopic: {}, reconciliationId: {}, status: {}, error: {}",
                event.getEventId(), correlationId, topic, event.getReconciliationId(), 
                event.getStatus(), exceptionMessage);
        
        Map<String, Object> dltMetadata = new HashMap<>();
        dltMetadata.put("originalTopic", topic);
        dltMetadata.put("reconciliationId", event.getReconciliationId());
        dltMetadata.put("settlementId", event.getSettlementId());
        dltMetadata.put("status", event.getStatus());
        dltMetadata.put("discrepancyAmount", event.getDiscrepancyAmount());
        dltMetadata.put("exceptionMessage", exceptionMessage);
        dltMetadata.put("eventTimestamp", event.getTimestamp());
        dltMetadata.put("dltTimestamp", Instant.now());
        
        auditService.logDeadLetterEvent(
            event.getEventId() != null ? event.getEventId() : "UNKNOWN",
            TOPIC_NAME,
            "PAYMENT_RECONCILIATION_UPDATE_DLT",
            exceptionMessage,
            correlationId,
            dltMetadata
        );
    }
}