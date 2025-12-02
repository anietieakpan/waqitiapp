package com.waqiti.payment.kafka;

import com.waqiti.payment.event.PaymentInitiatedEvent;
import com.waqiti.payment.service.PaymentService;
import com.waqiti.payment.client.LedgerServiceClient;
import com.waqiti.payment.client.ReconciliationServiceClient;
import com.waqiti.common.kafka.ConsumerErrorHandler;
import com.waqiti.common.tracing.TraceableKafkaConsumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Production-ready Kafka consumer for PaymentInitiatedEvent
 * 
 * Handles payment initiation events by:
 * - Recording transaction in ledger
 * - Triggering reconciliation tracking
 * - Updating internal payment state
 * - Publishing downstream events
 * 
 * Features:
 * - Automatic retry with exponential backoff
 * - Dead letter queue handling
 * - Distributed tracing
 * - Transaction management
 * - Comprehensive error handling
 * 
 * @author Waqiti Payment Team
 * @version 2.0.0
 * @since 2025-01-16
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentInitiatedEventConsumer extends TraceableKafkaConsumer {

    private final PaymentService paymentService;
    private final LedgerServiceClient ledgerServiceClient;
    private final ReconciliationServiceClient reconciliationServiceClient;
    private final ConsumerErrorHandler errorHandler;

    @KafkaListener(
        topics = "payment-initiated",
        groupId = "payment-service-payment-initiated",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 500, multiplier = 1.5, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {
            IllegalArgumentException.class,
            com.fasterxml.jackson.core.JsonProcessingException.class,
            org.springframework.kafka.support.serializer.DeserializationException.class
        }
    )
    @Transactional
    public void handlePaymentInitiated(
            @Payload PaymentInitiatedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            ConsumerRecord<String, PaymentInitiatedEvent> record,
            Acknowledgment acknowledgment) {

        String correlationId = event.getCorrelationId();
        String paymentId = event.getPaymentId();

        log.info("Processing PaymentInitiatedEvent - paymentId: {}, correlationId: {}, amount: {}, partition: {}, offset: {}", 
            paymentId, correlationId, event.getAmount(), partition, offset);

        try {
            // Start distributed trace
            startTrace("payment-initiated-consumer", correlationId, Map.of(
                "payment.id", paymentId,
                "payment.amount", event.getAmount().toString(),
                "payment.currency", event.getCurrency(),
                "payment.method", event.getPaymentMethod(),
                "kafka.topic", topic,
                "kafka.partition", String.valueOf(partition),
                "kafka.offset", String.valueOf(offset)
            ));

            // Process the event
            processPaymentInitiated(event);

            // Manual acknowledgment
            acknowledgment.acknowledge();

            log.info("PaymentInitiatedEvent processed successfully - paymentId: {}, correlationId: {}", 
                paymentId, correlationId);

        } catch (Exception e) {
            log.error("Failed to process PaymentInitiatedEvent - paymentId: {}, correlationId: {}", 
                paymentId, correlationId, e);

            // Handle error with retry/DLQ logic
            errorHandler.handleConsumerError(record, e, "payment-initiated", acknowledgment);
            
            recordException(e);
            throw e; // Re-throw to trigger retry mechanism

        } finally {
            endTrace();
        }
    }

    /**
     * Process payment initiated event
     */
    private void processPaymentInitiated(PaymentInitiatedEvent event) {
        // 1. Record in ledger
        recordInLedger(event);

        // 2. Track for reconciliation
        trackForReconciliation(event);

        // 3. Update payment status
        updatePaymentStatus(event);

        // 4. Publish downstream events
        publishDownstreamEvents(event);
    }

    /**
     * Record payment initiation in double-entry ledger
     */
    private void recordInLedger(PaymentInitiatedEvent event) {
        try {
            log.debug("Recording payment in ledger - paymentId: {}", event.getPaymentId());

            LedgerEntryRequest ledgerRequest = LedgerEntryRequest.builder()
                .transactionId(event.getPaymentId())
                .correlationId(event.getCorrelationId())
                .transactionType("PAYMENT_INITIATED")
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .fromAccount(event.getFromAccountId())
                .toAccount(event.getToAccountId())
                .description("Payment initiated: " + event.getDescription())
                .metadata(Map.of(
                    "paymentMethod", event.getPaymentMethod(),
                    "initiatedBy", event.getInitiatedBy(),
                    "initiatedAt", event.getInitiatedAt().toString()
                ))
                .build();

            LedgerEntryResponse response = ledgerServiceClient.createDoubleEntry(ledgerRequest);

            if (!response.isSuccessful()) {
                throw new RuntimeException("Ledger entry failed: " + response.getErrorMessage());
            }

            log.debug("Ledger entry created successfully - paymentId: {}, ledgerEntryId: {}", 
                event.getPaymentId(), response.getLedgerEntryId());

        } catch (Exception e) {
            log.error("Failed to record payment in ledger - paymentId: {}", event.getPaymentId(), e);
            throw new RuntimeException("Ledger recording failed", e);
        }
    }

    /**
     * Track payment for reconciliation
     */
    private void trackForReconciliation(PaymentInitiatedEvent event) {
        try {
            log.debug("Tracking payment for reconciliation - paymentId: {}", event.getPaymentId());

            ReconciliationTrackingRequest trackingRequest = ReconciliationTrackingRequest.builder()
                .paymentId(event.getPaymentId())
                .correlationId(event.getCorrelationId())
                .transactionType("PAYMENT")
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .initiatedAt(event.getInitiatedAt())
                .expectedCompletionAt(event.getInitiatedAt().plusMinutes(30))
                .paymentMethod(event.getPaymentMethod())
                .provider(event.getProvider())
                .metadata(Map.of(
                    "fromAccount", event.getFromAccountId(),
                    "toAccount", event.getToAccountId(),
                    "initiatedBy", event.getInitiatedBy()
                ))
                .build();

            ReconciliationTrackingResponse response = reconciliationServiceClient.trackPayment(trackingRequest);

            if (!response.isSuccessful()) {
                log.warn("Reconciliation tracking failed - paymentId: {}, error: {}", 
                    event.getPaymentId(), response.getErrorMessage());
                // Don't fail the entire process for reconciliation tracking issues
            }

        } catch (Exception e) {
            log.error("Failed to track payment for reconciliation - paymentId: {}", event.getPaymentId(), e);
            // Don't fail the entire process for reconciliation tracking issues
        }
    }

    /**
     * Update internal payment status
     */
    private void updatePaymentStatus(PaymentInitiatedEvent event) {
        try {
            paymentService.updatePaymentStatus(
                event.getPaymentId(), 
                PaymentStatus.INITIATED, 
                "Payment initiated successfully"
            );

        } catch (Exception e) {
            log.error("Failed to update payment status - paymentId: {}", event.getPaymentId(), e);
            throw new RuntimeException("Payment status update failed", e);
        }
    }

    /**
     * Publish downstream events
     */
    private void publishDownstreamEvents(PaymentInitiatedEvent event) {
        try {
            // Publish payment tracking event
            PaymentTrackingEvent trackingEvent = PaymentTrackingEvent.builder()
                .paymentId(event.getPaymentId())
                .correlationId(event.getCorrelationId())
                .status(PaymentStatus.INITIATED)
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .timestamp(LocalDateTime.now())
                .build();

            kafkaTemplate.send("payment-tracking", event.getPaymentId(), trackingEvent);

            // Publish fraud detection trigger
            FraudDetectionTriggerEvent fraudEvent = FraudDetectionTriggerEvent.builder()
                .paymentId(event.getPaymentId())
                .correlationId(event.getCorrelationId())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .fromAccountId(event.getFromAccountId())
                .toAccountId(event.getToAccountId())
                .paymentMethod(event.getPaymentMethod())
                .userAgent(event.getUserAgent())
                .ipAddress(event.getIpAddress())
                .deviceFingerprint(event.getDeviceFingerprint())
                .timestamp(LocalDateTime.now())
                .build();

            kafkaTemplate.send("fraud-detection-trigger", event.getPaymentId(), fraudEvent);

            log.debug("Downstream events published successfully - paymentId: {}", event.getPaymentId());

        } catch (Exception e) {
            log.error("Failed to publish downstream events - paymentId: {}", event.getPaymentId(), e);
            // Don't fail the entire process for downstream event publishing issues
        }
    }

    /**
     * DLQ handler for failed critical financial messages
     * Enhanced with comprehensive error analysis and alerting
     */
    @KafkaListener(
        topics = "payment-initiated-payment-dlq",
        groupId = "payment-service-dlq-handler",
        containerFactory = "operationalKafkaListenerContainerFactory"
    )
    public void handleDlq(
            @Payload PaymentInitiatedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "x-original-topic", required = false) String originalTopic,
            @Header(value = "x-error-message", required = false) String errorMessage,
            @Header(value = "x-error-class", required = false) String errorClass,
            @Header(value = "x-retry-count", required = false) String retryCount,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {

        log.error("CRITICAL_DLQ: PaymentInitiatedEvent sent to DLQ - paymentId: {}, originalTopic: {}, error: {}, errorClass: {}, retries: {}", 
            event.getPaymentId(), originalTopic, errorMessage, errorClass, retryCount);

        try {
            // Comprehensive error analysis
            DlqAnalysis analysis = analyzeDlqError(event, errorMessage, errorClass, retryCount);
            
            // Send immediate critical alert for payment failures
            sendCriticalPaymentAlert(event, analysis);
            
            // Mark payment as failed with detailed error context
            paymentService.updatePaymentStatus(
                event.getPaymentId(),
                PaymentStatus.DLQ_FAILED,
                String.format("DLQ Processing failed: %s (Class: %s, Retries: %s)", 
                    errorMessage, errorClass, retryCount)
            );
            
            // Store for manual review and potential replay
            storeDlqMessageForReview(event, analysis);
            
            // Notify customer service for high-value payments
            if (event.getAmount().compareTo(java.math.BigDecimal.valueOf(10000)) > 0) {
                notifyCustomerService(event, analysis);
            }
            
            // Update DLQ metrics
            updateDlqMetrics(event, analysis);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to handle DLQ message for payment: {} - This requires immediate manual intervention", 
                event.getPaymentId(), e);
            
            // Send escalated alert for DLQ handler failure
            sendEscalatedAlert(event, e);
        }
    }
    
    /**
     * Legacy DLT handler for backward compatibility
     */
    @KafkaListener(
        topics = "payment-initiated.DLT",
        groupId = "payment-service-legacy-dlt"
    )
    public void handleLegacyDlt(
            @Payload PaymentInitiatedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String errorMessage) {

        log.warn("LEGACY_DLT: PaymentInitiatedEvent sent to legacy DLT - paymentId: {}, redirecting to modern DLQ handling", 
            event.getPaymentId());

        // Redirect to modern DLQ handling
        handleDlq(event, topic, "payment-initiated", errorMessage, "UnknownException", "unknown", System.currentTimeMillis());
    }
    
    // DLQ helper methods
    
    private DlqAnalysis analyzeDlqError(PaymentInitiatedEvent event, String errorMessage, String errorClass, String retryCount) {
        return DlqAnalysis.builder()
            .paymentId(event.getPaymentId())
            .amount(event.getAmount())
            .currency(event.getCurrency())
            .paymentMethod(event.getPaymentMethod())
            .errorMessage(errorMessage)
            .errorClass(errorClass)
            .retryCount(retryCount)
            .severity(determineSeverity(event, errorClass))
            .isRetryable(isRetryableError(errorClass))
            .requiresManualReview(requiresManualReview(event, errorClass))
            .build();
    }
    
    private void sendCriticalPaymentAlert(PaymentInitiatedEvent event, DlqAnalysis analysis) {
        try {
            Map<String, Object> alertContext = Map.of(
                "paymentId", event.getPaymentId(),
                "amount", event.getAmount(),
                "currency", event.getCurrency(),
                "paymentMethod", event.getPaymentMethod(),
                "severity", analysis.getSeverity(),
                "errorClass", analysis.getErrorClass(),
                "errorMessage", analysis.getErrorMessage(),
                "isRetryable", analysis.isRetryable(),
                "requiresManualReview", analysis.requiresManualReview()
            );
            
            // Send to multiple channels for critical payment failures
            alertService.sendCriticalAlert(
                "CRITICAL PAYMENT DLQ FAILURE",
                String.format("Payment %s (%s %s) failed processing and sent to DLQ", 
                    event.getPaymentId(), event.getAmount(), event.getCurrency()),
                alertContext
            );
            
            // Send to PagerDuty for immediate response
            alertService.sendPagerDutyAlert(
                "payment-processing-failure",
                String.format("Payment %s DLQ - Amount: %s %s - Error: %s", 
                    event.getPaymentId(), event.getAmount(), event.getCurrency(), analysis.getErrorClass()),
                alertContext
            );
            
        } catch (Exception e) {
            log.error("Failed to send critical payment alert", e);
        }
    }
    
    private void storeDlqMessageForReview(PaymentInitiatedEvent event, DlqAnalysis analysis) {
        try {
            DlqRecord record = DlqRecord.builder()
                .id(java.util.UUID.randomUUID().toString())
                .originalTopic("payment-initiated")
                .dlqTopic("payment-initiated-payment-dlq")
                .paymentId(event.getPaymentId())
                .eventData(objectMapper.writeValueAsString(event))
                .analysis(analysis)
                .status(DlqRecordStatus.PENDING_REVIEW)
                .createdAt(LocalDateTime.now())
                .build();
                
            dlqRecordRepository.save(record);
            
        } catch (Exception e) {
            log.error("Failed to store DLQ record for review", e);
        }
    }
    
    private void notifyCustomerService(PaymentInitiatedEvent event, DlqAnalysis analysis) {
        try {
            CustomerServiceNotification notification = CustomerServiceNotification.builder()
                .paymentId(event.getPaymentId())
                .customerId(event.getFromAccountId())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .errorSummary(analysis.getErrorMessage())
                .priority(Priority.HIGH)
                .requiresCallback(true)
                .build();
                
            customerServiceNotificationService.sendNotification(notification);
            
        } catch (Exception e) {
            log.error("Failed to notify customer service", e);
        }
    }
    
    private void updateDlqMetrics(PaymentInitiatedEvent event, DlqAnalysis analysis) {
        try {
            errorMetrics.incrementDlqCount("payment-initiated", "payment-service", analysis.getErrorClass());
            errorMetrics.recordCriticalError("payment-initiated", "payment-service", 
                new RuntimeException(analysis.getErrorMessage()));
                
        } catch (Exception e) {
            log.error("Failed to update DLQ metrics", e);
        }
    }
    
    private void sendEscalatedAlert(PaymentInitiatedEvent event, Exception e) {
        try {
            alertService.sendEscalatedAlert(
                "DLQ HANDLER FAILURE - MANUAL INTERVENTION REQUIRED",
                String.format("DLQ handler failed for payment %s - Error: %s", 
                    event.getPaymentId(), e.getMessage()),
                Map.of(
                    "paymentId", event.getPaymentId(),
                    "amount", event.getAmount(),
                    "dlqHandlerError", e.getMessage(),
                    "requiresImmediateAttention", true
                )
            );
        } catch (Exception alertE) {
            log.error("CRITICAL: Failed to send escalated alert - System requires immediate manual attention", alertE);
        }
    }
    
    private String determineSeverity(PaymentInitiatedEvent event, String errorClass) {
        // High-value payments get CRITICAL severity
        if (event.getAmount().compareTo(java.math.BigDecimal.valueOf(50000)) > 0) {
            return "CRITICAL";
        }
        
        // Database/infrastructure errors are HIGH severity
        if (errorClass != null && (errorClass.contains("DataAccess") || errorClass.contains("Connection"))) {
            return "HIGH";
        }
        
        return "MEDIUM";
    }
    
    private boolean isRetryableError(String errorClass) {
        if (errorClass == null) return false;
        
        return errorClass.contains("DataAccess") ||
               errorClass.contains("ResourceAccess") ||
               errorClass.contains("Timeout") ||
               errorClass.contains("Connection");
    }
    
    private boolean requiresManualReview(PaymentInitiatedEvent event, String errorClass) {
        // High-value payments always require manual review
        if (event.getAmount().compareTo(java.math.BigDecimal.valueOf(10000)) > 0) {
            return true;
        }
        
        // Certain error types require manual review
        return errorClass != null && (
            errorClass.contains("ValidationException") ||
            errorClass.contains("BusinessRuleViolation") ||
            errorClass.contains("FraudDetection")
        );
    }
    
    // Data classes for DLQ handling
    
    @lombok.Data
    @lombok.Builder
    private static class DlqAnalysis {
        private String paymentId;
        private java.math.BigDecimal amount;
        private String currency;
        private String paymentMethod;
        private String errorMessage;
        private String errorClass;
        private String retryCount;
        private String severity;
        private boolean isRetryable;
        private boolean requiresManualReview;
    }
}