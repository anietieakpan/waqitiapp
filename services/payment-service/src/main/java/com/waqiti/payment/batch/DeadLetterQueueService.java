package com.waqiti.payment.batch;

import com.waqiti.payment.dto.PaymentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Production-grade Dead Letter Queue service for failed payment handling
 * Manages failed payments with retry logic, escalation, and comprehensive tracking
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeadLetterQueueService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final DeadLetterQueueRepository deadLetterQueueRepository;
    private final BatchAuditService batchAuditService;

    @Value("${batch.payment.dlq.topic:payment-dlq}")
    private String deadLetterQueueTopic;

    @Value("${batch.payment.dlq.retry-topic:payment-dlq-retry}")
    private String retryTopic;

    @Value("${batch.payment.dlq.max-retry-attempts:5}")
    private int maxRetryAttempts;

    @Value("${batch.payment.dlq.initial-retry-delay-minutes:5}")
    private int initialRetryDelayMinutes;

    @Value("${batch.payment.dlq.escalation-threshold-hours:24}")
    private int escalationThresholdHours;

    /**
     * Send failed payment to dead letter queue
     */
    @Transactional
    public void sendToDeadLetterQueue(PaymentRequest payment, String errorMessage) {
        log.warn("SECURITY: Sending payment to dead letter queue: {}, error: {}", 
                payment.getPaymentId(), errorMessage);

        try {
            // Create DLQ record
            DeadLetterQueueRecord dlqRecord = createDeadLetterQueueRecord(payment, errorMessage);
            DeadLetterQueueRecord savedRecord = deadLetterQueueRepository.save(dlqRecord);

            // Send to Kafka DLQ topic
            DeadLetterQueueMessage dlqMessage = createDeadLetterQueueMessage(savedRecord, payment);
            
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(
                    deadLetterQueueTopic, 
                    payment.getPaymentId(), 
                    dlqMessage.toJson());

            future.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("CRITICAL: Failed to send payment to DLQ topic: {}", 
                            payment.getPaymentId(), throwable);
                    
                    // Mark as failed to send to queue
                    updateDlqRecordStatus(savedRecord.getId(), DeadLetterQueueStatus.QUEUE_SEND_FAILED);
                } else {
                    log.debug("SECURITY: Payment successfully sent to DLQ topic: {}", 
                            payment.getPaymentId());
                    
                    updateDlqRecordStatus(savedRecord.getId(), DeadLetterQueueStatus.IN_QUEUE);
                }
            });

        } catch (Exception e) {
            log.error("CRITICAL: Failed to process payment for dead letter queue: {}", 
                    payment.getPaymentId(), e);
        }
    }

    /**
     * Process retry for failed payments (scheduled job)
     */
    @Scheduled(fixedDelayString = "${batch.payment.dlq.retry-check-interval:300000}") // 5 minutes
    @Transactional
    public void processRetryQueue() {
        log.debug("SECURITY: Processing DLQ retry queue");

        try {
            // Find records eligible for retry
            List<DeadLetterQueueRecord> eligibleForRetry = deadLetterQueueRepository
                    .findEligibleForRetry(LocalDateTime.now());

            log.info("SECURITY: Found {} payments eligible for retry", eligibleForRetry.size());

            for (DeadLetterQueueRecord dlqRecord : eligibleForRetry) {
                processRetryAttempt(dlqRecord);
            }

            // Check for records needing escalation
            List<DeadLetterQueueRecord> needingEscalation = deadLetterQueueRepository
                    .findNeedingEscalation(LocalDateTime.now().minusHours(escalationThresholdHours));

            for (DeadLetterQueueRecord dlqRecord : needingEscalation) {
                escalateDeadLetterRecord(dlqRecord);
            }

        } catch (Exception e) {
            log.error("CRITICAL: Error processing DLQ retry queue", e);
        }
    }

    /**
     * Process individual retry attempt
     */
    @Async
    @Transactional
    public void processRetryAttempt(DeadLetterQueueRecord dlqRecord) {
        log.info("SECURITY: Processing retry attempt {} for payment: {}", 
                dlqRecord.getRetryAttempts() + 1, dlqRecord.getPaymentId());

        try {
            // Update retry attempt count
            dlqRecord.setRetryAttempts(dlqRecord.getRetryAttempts() + 1);
            dlqRecord.setLastRetryAttempt(LocalDateTime.now());
            dlqRecord.setStatus(DeadLetterQueueStatus.RETRYING);

            // Calculate next retry time with exponential backoff
            LocalDateTime nextRetryTime = calculateNextRetryTime(dlqRecord.getRetryAttempts());
            dlqRecord.setNextRetryAttempt(nextRetryTime);

            deadLetterQueueRepository.save(dlqRecord);

            // Send to retry topic
            RetryMessage retryMessage = RetryMessage.builder()
                    .dlqRecordId(dlqRecord.getId())
                    .paymentId(dlqRecord.getPaymentId())
                    .retryAttempt(dlqRecord.getRetryAttempts())
                    .originalError(dlqRecord.getErrorMessage())
                    .retryTimestamp(LocalDateTime.now())
                    .build();

            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(
                    retryTopic, 
                    dlqRecord.getPaymentId(), 
                    retryMessage.toJson());

            future.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("CRITICAL: Failed to send retry message for payment: {}", 
                            dlqRecord.getPaymentId(), throwable);
                    
                    updateDlqRecordStatus(dlqRecord.getId(), DeadLetterQueueStatus.RETRY_FAILED);
                } else {
                    log.debug("SECURITY: Retry message sent successfully for payment: {}", 
                            dlqRecord.getPaymentId());
                    
                    updateDlqRecordStatus(dlqRecord.getId(), DeadLetterQueueStatus.RETRY_SENT);
                }
            });

        } catch (Exception e) {
            log.error("CRITICAL: Error processing retry attempt for payment: {}", 
                    dlqRecord.getPaymentId(), e);
            
            updateDlqRecordStatus(dlqRecord.getId(), DeadLetterQueueStatus.RETRY_FAILED);
        }
    }

    /**
     * Mark payment as successfully processed (remove from DLQ)
     */
    @Transactional
    public void markAsProcessed(String paymentId, String transactionId) {
        log.info("SECURITY: Marking DLQ payment as processed: {}", paymentId);

        DeadLetterQueueRecord dlqRecord = deadLetterQueueRepository
                .findByPaymentId(paymentId)
                .orElse(null);

        if (dlqRecord != null) {
            dlqRecord.setStatus(DeadLetterQueueStatus.PROCESSED);
            dlqRecord.setResolvedAt(LocalDateTime.now());
            dlqRecord.setTransactionId(transactionId);
            
            deadLetterQueueRepository.save(dlqRecord);

            log.info("SECURITY: DLQ payment marked as processed: {}", paymentId);
        }
    }

    /**
     * Mark payment as permanently failed (after max retries)
     */
    @Transactional
    public void markAsPermanentlyFailed(UUID dlqRecordId, String finalErrorMessage) {
        log.error("SECURITY: Marking DLQ payment as permanently failed: {}", dlqRecordId);

        DeadLetterQueueRecord dlqRecord = deadLetterQueueRepository.findById(dlqRecordId)
                .orElseThrow(() -> new IllegalArgumentException("DLQ record not found: " + dlqRecordId));

        dlqRecord.setStatus(DeadLetterQueueStatus.PERMANENTLY_FAILED);
        dlqRecord.setFinalErrorMessage(finalErrorMessage);
        dlqRecord.setResolvedAt(LocalDateTime.now());

        deadLetterQueueRepository.save(dlqRecord);

        // Trigger manual review process
        triggerManualReview(dlqRecord);
    }

    /**
     * Escalate DLQ record for manual intervention
     */
    @Transactional
    public void escalateDeadLetterRecord(DeadLetterQueueRecord dlqRecord) {
        log.warn("SECURITY: Escalating DLQ record for manual review: {}", dlqRecord.getId());

        dlqRecord.setStatus(DeadLetterQueueStatus.ESCALATED);
        dlqRecord.setEscalatedAt(LocalDateTime.now());
        dlqRecord.setEscalationReason("Maximum retry attempts exceeded and time threshold reached");

        deadLetterQueueRepository.save(dlqRecord);

        // Send escalation notification
        sendEscalationNotification(dlqRecord);

        // Log security event
        batchAuditService.logSecurityEvent(
                dlqRecord.getBatchAuditRecordId(),
                BatchAuditService.SecurityEventType.PROCESSING_TIMEOUT,
                "Payment escalated to manual review after " + dlqRecord.getRetryAttempts() + " retry attempts",
                "Payment ID: " + dlqRecord.getPaymentId());
    }

    /**
     * Get DLQ statistics for monitoring
     */
    public DeadLetterQueueStatistics getDeadLetterQueueStatistics(LocalDateTime startDate, 
                                                                LocalDateTime endDate) {
        return deadLetterQueueRepository.getStatistics(startDate, endDate);
    }

    /**
     * Find DLQ records by criteria for investigation
     */
    public List<DeadLetterQueueRecord> findDeadLetterQueueRecords(DeadLetterQueueSearchCriteria criteria) {
        return deadLetterQueueRepository.findByCriteria(criteria);
    }

    /**
     * Utility methods
     */
    private DeadLetterQueueRecord createDeadLetterQueueRecord(PaymentRequest payment, String errorMessage) {
        return DeadLetterQueueRecord.builder()
                .id(UUID.randomUUID())
                .paymentId(payment.getPaymentId())
                .batchId(payment.getBatchId() != null ? payment.getBatchId() : "UNKNOWN")
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .payerId(payment.getPayerId())
                .recipientId(payment.getRecipientId())
                .errorMessage(errorMessage)
                .status(DeadLetterQueueStatus.PENDING)
                .retryAttempts(0)
                .createdAt(LocalDateTime.now())
                .nextRetryAttempt(calculateNextRetryTime(0))
                .build();
    }

    private DeadLetterQueueMessage createDeadLetterQueueMessage(DeadLetterQueueRecord dlqRecord, 
                                                               PaymentRequest payment) {
        return DeadLetterQueueMessage.builder()
                .dlqRecordId(dlqRecord.getId())
                .paymentId(payment.getPaymentId())
                .originalPayment(payment)
                .errorMessage(dlqRecord.getErrorMessage())
                .createdAt(dlqRecord.getCreatedAt())
                .retryAttempts(dlqRecord.getRetryAttempts())
                .build();
    }

    private LocalDateTime calculateNextRetryTime(int retryAttempt) {
        // Exponential backoff: 5, 10, 20, 40, 80 minutes
        int delayMinutes = initialRetryDelayMinutes * (int) Math.pow(2, retryAttempt);
        return LocalDateTime.now().plusMinutes(Math.min(delayMinutes, 1440)); // Cap at 24 hours
    }

    private void updateDlqRecordStatus(UUID dlqRecordId, DeadLetterQueueStatus status) {
        try {
            DeadLetterQueueRecord record = deadLetterQueueRepository.findById(dlqRecordId).orElse(null);
            if (record != null) {
                record.setStatus(status);
                record.setUpdatedAt(LocalDateTime.now());
                deadLetterQueueRepository.save(record);
            }
        } catch (Exception e) {
            log.error("CRITICAL: Failed to update DLQ record status: {}", dlqRecordId, e);
        }
    }

    private void triggerManualReview(DeadLetterQueueRecord dlqRecord) {
        // In production, this would create tickets in JIRA, ServiceNow, etc.
        log.error("MANUAL REVIEW REQUIRED: Payment permanently failed - {}", dlqRecord.getPaymentId());
    }

    private void sendEscalationNotification(DeadLetterQueueRecord dlqRecord) {
        // In production, this would send emails, Slack notifications, etc.
        log.error("ESCALATION: DLQ payment requires manual intervention - {}", dlqRecord.getPaymentId());
    }

    // Enums and DTOs
    public enum DeadLetterQueueStatus {
        PENDING,
        IN_QUEUE,
        RETRYING,
        RETRY_SENT,
        RETRY_FAILED,
        PROCESSED,
        PERMANENTLY_FAILED,
        ESCALATED,
        QUEUE_SEND_FAILED
    }

    @lombok.Builder
    @lombok.Data
    public static class DeadLetterQueueRecord {
        private UUID id;
        private String paymentId;
        private String batchId;
        private java.math.BigDecimal amount;
        private String currency;
        private UUID payerId;
        private UUID recipientId;
        private String errorMessage;
        private String finalErrorMessage;
        private DeadLetterQueueStatus status;
        private Integer retryAttempts;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime nextRetryAttempt;
        private LocalDateTime lastRetryAttempt;
        private LocalDateTime resolvedAt;
        private LocalDateTime escalatedAt;
        private String escalationReason;
        private String transactionId;
        private UUID batchAuditRecordId;
    }

    @lombok.Builder
    @lombok.Data
    public static class DeadLetterQueueMessage {
        private UUID dlqRecordId;
        private String paymentId;
        private PaymentRequest originalPayment;
        private String errorMessage;
        private LocalDateTime createdAt;
        private Integer retryAttempts;

        public String toJson() {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(this);
            } catch (Exception e) {
                return "{}";
            }
        }
    }

    @lombok.Builder
    @lombok.Data
    public static class RetryMessage {
        private UUID dlqRecordId;
        private String paymentId;
        private Integer retryAttempt;
        private String originalError;
        private LocalDateTime retryTimestamp;

        public String toJson() {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(this);
            } catch (Exception e) {
                return "{}";
            }
        }
    }

    @lombok.Builder
    @lombok.Data
    public static class DeadLetterQueueStatistics {
        private LocalDateTime periodStart;
        private LocalDateTime periodEnd;
        private Long totalRecords;
        private Long pendingRecords;
        private Long retryingRecords;
        private Long processedRecords;
        private Long permanentlyFailedRecords;
        private Long escalatedRecords;
        private Double averageRetryAttempts;
        private java.math.BigDecimal totalAmountInQueue;
        private Long oldestRecordDays;
    }

    @lombok.Builder
    @lombok.Data
    public static class DeadLetterQueueSearchCriteria {
        private String paymentId;
        private String batchId;
        private DeadLetterQueueStatus status;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private java.math.BigDecimal minAmount;
        private java.math.BigDecimal maxAmount;
        private String currency;
        private Integer minRetryAttempts;
        private Integer maxRetryAttempts;
    }
}