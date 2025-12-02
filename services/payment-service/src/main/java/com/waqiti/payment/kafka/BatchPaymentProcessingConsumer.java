package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.payment.service.BatchPaymentService;
import com.waqiti.payment.service.PaymentReconciliationService;
import com.waqiti.payment.service.PaymentNotificationService;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consumer for batch payment completion events
 * 
 * CRITICAL PAYMENT PROCESSING CONSUMER
 * Processes completion events for batch payments (payroll, bulk transfers, etc.)
 * 
 * KEY FEATURES:
 * - Process batch completion status
 * - Handle partial success scenarios
 * - Trigger reconciliation
 * - Generate reports
 * - Notify stakeholders
 * - Update payment states
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BatchPaymentProcessingConsumer {
    
    private final BatchPaymentService batchPaymentService;
    private final PaymentReconciliationService reconciliationService;
    private final PaymentNotificationService notificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"batch-payment-completion", "batch-payment-completion-retry"},
        groupId = "payment-service-batch-completion-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void handleBatchPaymentCompletion(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("BATCH PAYMENT COMPLETION: Processing batch completion - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        UUID batchId = null;
        UUID submitterId = null;
        String batchStatus = null;
        
        try {
            // Parse batch payment completion event
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            batchId = UUID.fromString((String) event.get("batchId"));
            submitterId = UUID.fromString((String) event.get("submitterId"));
            String batchType = (String) event.get("batchType"); // PAYROLL, BULK_TRANSFER, VENDOR_PAYMENT
            batchStatus = (String) event.get("batchStatus"); // COMPLETED, PARTIAL_SUCCESS, FAILED
            Integer totalPayments = (Integer) event.get("totalPayments");
            Integer successfulPayments = (Integer) event.get("successfulPayments");
            Integer failedPayments = (Integer) event.get("failedPayments");
            BigDecimal totalAmount = new BigDecimal(event.get("totalAmount").toString());
            BigDecimal successfulAmount = new BigDecimal(event.get("successfulAmount").toString());
            BigDecimal failedAmount = new BigDecimal(event.get("failedAmount").toString());
            String currency = (String) event.get("currency");
            LocalDateTime completionTime = LocalDateTime.parse((String) event.get("completionTime"));
            @SuppressWarnings("unchecked")
            List<String> failedPaymentIds = (List<String>) event.getOrDefault("failedPaymentIds", List.of());
            @SuppressWarnings("unchecked")
            List<Map<String, String>> failureReasons = (List<Map<String, String>>) event.getOrDefault("failureReasons", List.of());
            Long processingDurationMs = event.containsKey("processingDurationMs") ? 
                    ((Number) event.get("processingDurationMs")).longValue() : null;
            
            log.info("Batch payment completion - BatchId: {}, Type: {}, Status: {}, Total: {}, Success: {}, Failed: {}, Amount: {} {}", 
                    batchId, batchType, batchStatus, totalPayments, successfulPayments, failedPayments, 
                    totalAmount, currency);
            
            // Validate batch completion event
            validateBatchCompletion(batchId, submitterId, batchStatus, totalPayments, 
                    successfulPayments, failedPayments, totalAmount);
            
            // Update batch status
            updateBatchStatus(batchId, batchStatus, successfulPayments, failedPayments, 
                    successfulAmount, failedAmount, completionTime, processingDurationMs);
            
            // Process based on batch status
            processBatchCompletionByStatus(batchId, submitterId, batchType, batchStatus, 
                    totalPayments, successfulPayments, failedPayments, totalAmount, 
                    successfulAmount, failedAmount, currency, failedPaymentIds, failureReasons);
            
            // Trigger reconciliation
            triggerBatchReconciliation(batchId, batchType, totalPayments, successfulPayments, 
                    failedPayments, totalAmount, successfulAmount, currency);
            
            // Generate batch completion report
            generateBatchReport(batchId, submitterId, batchType, batchStatus, totalPayments, 
                    successfulPayments, failedPayments, totalAmount, successfulAmount, 
                    failedAmount, currency, failedPaymentIds, failureReasons);
            
            // Handle failed payments
            if (failedPayments > 0) {
                handleFailedPayments(batchId, submitterId, batchType, failedPaymentIds, 
                        failureReasons, failedAmount, currency);
            }
            
            // Notify stakeholders
            notifyStakeholders(batchId, submitterId, batchType, batchStatus, totalPayments, 
                    successfulPayments, failedPayments, totalAmount, successfulAmount, currency);
            
            // Update metrics
            updateBatchMetrics(batchType, batchStatus, totalPayments, successfulPayments, 
                    failedPayments, totalAmount, processingDurationMs);
            
            // Comprehensive audit trail
            auditBatchCompletion(batchId, submitterId, batchType, batchStatus, totalPayments, 
                    successfulPayments, failedPayments, totalAmount, successfulAmount, 
                    currency, processingStartTime);
            
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            
            log.info("Batch payment completion processed - BatchId: {}, Status: {}, Success: {}/{}, ProcessingTime: {}ms", 
                    batchId, batchStatus, successfulPayments, totalPayments, processingTimeMs);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Batch payment completion processing failed - BatchId: {}, Status: {}, Error: {}", 
                    batchId, batchStatus, e.getMessage(), e);
            
            if (batchId != null) {
                handleBatchCompletionFailure(batchId, submitterId, batchStatus, e);
            }
            
            throw new RuntimeException("Batch payment completion processing failed", e);
        }
    }
    
    private void validateBatchCompletion(UUID batchId, UUID submitterId, String batchStatus,
                                        Integer totalPayments, Integer successfulPayments,
                                        Integer failedPayments, BigDecimal totalAmount) {
        if (batchId == null || submitterId == null) {
            throw new IllegalArgumentException("Batch ID and Submitter ID are required");
        }
        
        if (batchStatus == null || batchStatus.trim().isEmpty()) {
            throw new IllegalArgumentException("Batch status is required");
        }
        
        List<String> validStatuses = List.of("COMPLETED", "PARTIAL_SUCCESS", "FAILED");
        if (!validStatuses.contains(batchStatus)) {
            throw new IllegalArgumentException("Invalid batch status: " + batchStatus);
        }
        
        if (totalPayments == null || totalPayments <= 0) {
            throw new IllegalArgumentException("Invalid total payments count");
        }
        
        if (successfulPayments + failedPayments != totalPayments) {
            log.warn("Payment count mismatch - BatchId: {}, Total: {}, Success: {}, Failed: {}", 
                    batchId, totalPayments, successfulPayments, failedPayments);
        }
        
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid total amount");
        }
        
        log.debug("Batch completion validation passed - BatchId: {}", batchId);
    }
    
    private void updateBatchStatus(UUID batchId, String batchStatus, Integer successfulPayments,
                                  Integer failedPayments, BigDecimal successfulAmount,
                                  BigDecimal failedAmount, LocalDateTime completionTime,
                                  Long processingDurationMs) {
        try {
            batchPaymentService.updateBatchStatus(
                    batchId, batchStatus, successfulPayments, failedPayments,
                    successfulAmount, failedAmount, completionTime, processingDurationMs);
            
            log.info("Batch status updated - BatchId: {}, Status: {}", batchId, batchStatus);
            
        } catch (Exception e) {
            log.error("Failed to update batch status - BatchId: {}", batchId, e);
            throw new RuntimeException("Batch status update failed", e);
        }
    }
    
    private void processBatchCompletionByStatus(UUID batchId, UUID submitterId, String batchType,
                                               String batchStatus, Integer totalPayments,
                                               Integer successfulPayments, Integer failedPayments,
                                               BigDecimal totalAmount, BigDecimal successfulAmount,
                                               BigDecimal failedAmount, String currency,
                                               List<String> failedPaymentIds, 
                                               List<Map<String, String>> failureReasons) {
        try {
            switch (batchStatus) {
                case "COMPLETED" -> processCompletedBatch(batchId, submitterId, batchType, 
                        totalPayments, totalAmount, currency);
                case "PARTIAL_SUCCESS" -> processPartialSuccessBatch(batchId, submitterId, 
                        batchType, successfulPayments, failedPayments, successfulAmount, 
                        failedAmount, currency, failedPaymentIds, failureReasons);
                case "FAILED" -> processFailedBatch(batchId, submitterId, batchType, 
                        totalPayments, totalAmount, currency, failedPaymentIds, failureReasons);
                default -> {
                    log.warn("Unknown batch status: {}", batchStatus);
                }
            }
            
            log.debug("Batch completion status processing completed - BatchId: {}, Status: {}", 
                    batchId, batchStatus);
            
        } catch (Exception e) {
            log.error("Failed to process batch completion by status - BatchId: {}, Status: {}", 
                    batchId, batchStatus, e);
        }
    }
    
    private void processCompletedBatch(UUID batchId, UUID submitterId, String batchType,
                                      Integer totalPayments, BigDecimal totalAmount, String currency) {
        log.info("Processing completed batch - BatchId: {}, Type: {}, Payments: {}, Amount: {} {}", 
                batchId, batchType, totalPayments, totalAmount, currency);
        
        batchPaymentService.markBatchAsCompleted(batchId, submitterId);
        
        // Release any holds
        batchPaymentService.releaseCompletionHolds(batchId);
        
        // Update accounting
        batchPaymentService.finalizeAccounting(batchId, totalAmount, currency);
    }
    
    private void processPartialSuccessBatch(UUID batchId, UUID submitterId, String batchType,
                                           Integer successfulPayments, Integer failedPayments,
                                           BigDecimal successfulAmount, BigDecimal failedAmount,
                                           String currency, List<String> failedPaymentIds,
                                           List<Map<String, String>> failureReasons) {
        log.warn("Processing partial success batch - BatchId: {}, Type: {}, Success: {}, Failed: {}", 
                batchId, batchType, successfulPayments, failedPayments);
        
        batchPaymentService.markBatchAsPartialSuccess(batchId, submitterId, 
                successfulPayments, failedPayments);
        
        // Queue failed payments for retry
        batchPaymentService.queueFailedPaymentsForRetry(batchId, failedPaymentIds, failureReasons);
        
        // Update accounting for successful portion
        batchPaymentService.finalizePartialAccounting(batchId, successfulAmount, 
                failedAmount, currency);
    }
    
    private void processFailedBatch(UUID batchId, UUID submitterId, String batchType,
                                   Integer totalPayments, BigDecimal totalAmount, String currency,
                                   List<String> failedPaymentIds, List<Map<String, String>> failureReasons) {
        log.error("Processing failed batch - BatchId: {}, Type: {}, Payments: {}, Amount: {} {}", 
                batchId, batchType, totalPayments, totalAmount, currency);
        
        batchPaymentService.markBatchAsFailed(batchId, submitterId, failureReasons);
        
        // Reverse any partial processing
        batchPaymentService.reverseBatchProcessing(batchId, totalAmount, currency);
        
        // Create manual review task
        batchPaymentService.createManualReviewTask(batchId, submitterId, batchType, 
                failureReasons);
    }
    
    private void triggerBatchReconciliation(UUID batchId, String batchType, Integer totalPayments,
                                           Integer successfulPayments, Integer failedPayments,
                                           BigDecimal totalAmount, BigDecimal successfulAmount,
                                           String currency) {
        try {
            reconciliationService.reconcileBatch(
                    batchId, batchType, totalPayments, successfulPayments, failedPayments,
                    totalAmount, successfulAmount, currency);
            
            log.info("Batch reconciliation triggered - BatchId: {}", batchId);
            
        } catch (Exception e) {
            log.error("Failed to trigger batch reconciliation - BatchId: {}", batchId, e);
        }
    }
    
    private void generateBatchReport(UUID batchId, UUID submitterId, String batchType,
                                    String batchStatus, Integer totalPayments,
                                    Integer successfulPayments, Integer failedPayments,
                                    BigDecimal totalAmount, BigDecimal successfulAmount,
                                    BigDecimal failedAmount, String currency,
                                    List<String> failedPaymentIds, List<Map<String, String>> failureReasons) {
        try {
            batchPaymentService.generateCompletionReport(
                    batchId, submitterId, batchType, batchStatus, totalPayments,
                    successfulPayments, failedPayments, totalAmount, successfulAmount,
                    failedAmount, currency, failedPaymentIds, failureReasons);
            
            log.info("Batch completion report generated - BatchId: {}", batchId);
            
        } catch (Exception e) {
            log.error("Failed to generate batch report - BatchId: {}", batchId, e);
        }
    }
    
    private void handleFailedPayments(UUID batchId, UUID submitterId, String batchType,
                                     List<String> failedPaymentIds, List<Map<String, String>> failureReasons,
                                     BigDecimal failedAmount, String currency) {
        try {
            batchPaymentService.handleFailedPayments(
                    batchId, submitterId, batchType, failedPaymentIds, failureReasons,
                    failedAmount, currency);
            
            log.warn("Failed payments handled - BatchId: {}, FailedCount: {}", 
                    batchId, failedPaymentIds.size());
            
        } catch (Exception e) {
            log.error("Failed to handle failed payments - BatchId: {}", batchId, e);
        }
    }
    
    private void notifyStakeholders(UUID batchId, UUID submitterId, String batchType,
                                   String batchStatus, Integer totalPayments,
                                   Integer successfulPayments, Integer failedPayments,
                                   BigDecimal totalAmount, BigDecimal successfulAmount, String currency) {
        try {
            notificationService.sendBatchCompletionNotification(
                    batchId, submitterId, batchType, batchStatus, totalPayments,
                    successfulPayments, failedPayments, totalAmount, successfulAmount, currency);
            
            log.info("Stakeholders notified - BatchId: {}, Status: {}", batchId, batchStatus);
            
        } catch (Exception e) {
            log.error("Failed to notify stakeholders - BatchId: {}", batchId, e);
        }
    }
    
    private void updateBatchMetrics(String batchType, String batchStatus, Integer totalPayments,
                                   Integer successfulPayments, Integer failedPayments,
                                   BigDecimal totalAmount, Long processingDurationMs) {
        try {
            batchPaymentService.updateBatchMetrics(
                    batchType, batchStatus, totalPayments, successfulPayments, failedPayments,
                    totalAmount, processingDurationMs);
        } catch (Exception e) {
            log.error("Failed to update batch metrics - Type: {}, Status: {}", batchType, batchStatus, e);
        }
    }
    
    private void auditBatchCompletion(UUID batchId, UUID submitterId, String batchType,
                                     String batchStatus, Integer totalPayments,
                                     Integer successfulPayments, Integer failedPayments,
                                     BigDecimal totalAmount, BigDecimal successfulAmount,
                                     String currency, LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditPaymentEvent(
                    "BATCH_PAYMENT_COMPLETED",
                    submitterId.toString(),
                    String.format("Batch payment completed - Type: %s, Status: %s, Success: %d/%d, Amount: %s %s", 
                            batchType, batchStatus, successfulPayments, totalPayments, totalAmount, currency),
                    Map.of(
                            "batchId", batchId.toString(),
                            "submitterId", submitterId.toString(),
                            "batchType", batchType,
                            "batchStatus", batchStatus,
                            "totalPayments", totalPayments,
                            "successfulPayments", successfulPayments,
                            "failedPayments", failedPayments,
                            "totalAmount", totalAmount.toString(),
                            "successfulAmount", successfulAmount.toString(),
                            "currency", currency,
                            "processingTimeMs", processingTimeMs
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit batch completion - BatchId: {}", batchId, e);
        }
    }
    
    private void handleBatchCompletionFailure(UUID batchId, UUID submitterId, String batchStatus, Exception error) {
        try {
            batchPaymentService.handleCompletionFailure(batchId, submitterId, batchStatus, error.getMessage());
            
            auditService.auditPaymentEvent(
                    "BATCH_COMPLETION_PROCESSING_FAILED",
                    submitterId != null ? submitterId.toString() : "UNKNOWN",
                    "Failed to process batch completion: " + error.getMessage(),
                    Map.of(
                            "batchId", batchId.toString(),
                            "submitterId", submitterId != null ? submitterId.toString() : "UNKNOWN",
                            "batchStatus", batchStatus != null ? batchStatus : "UNKNOWN",
                            "error", error.getClass().getSimpleName(),
                            "errorMessage", error.getMessage()
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to handle batch completion failure - BatchId: {}", batchId, e);
        }
    }
    
    @KafkaListener(
        topics = "batch-payment-completion.DLQ",
        groupId = "payment-service-batch-completion-dlq-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlq(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "x-original-topic", required = false) String originalTopic,
            @Header(value = "x-error-message", required = false) String errorMessage,
            @Header(value = "x-error-class", required = false) String errorClass,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        log.error("CRITICAL: Batch payment completion sent to DLQ - OriginalTopic: {}, Error: {}, ErrorClass: {}, Event: {}", 
                originalTopic, errorMessage, errorClass, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            UUID batchId = event.containsKey("batchId") ? 
                    UUID.fromString((String) event.get("batchId")) : null;
            UUID submitterId = event.containsKey("submitterId") ? 
                    UUID.fromString((String) event.get("submitterId")) : null;
            String batchStatus = (String) event.get("batchStatus");
            
            log.error("DLQ: Batch payment completion failed permanently - BatchId: {}, SubmitterId: {}, Status: {} - MANUAL INTERVENTION REQUIRED", 
                    batchId, submitterId, batchStatus);
            
            if (batchId != null) {
                batchPaymentService.markForManualReview(batchId, submitterId, batchStatus, 
                        "DLQ: " + errorMessage);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse batch completion DLQ event: {}", eventJson, e);
        }
    }
}