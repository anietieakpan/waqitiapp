package com.waqiti.recurringpayment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.recurringpayment.service.RecurringPaymentService;
import com.waqiti.recurringpayment.service.RecurringPaymentSchedulerService;
import com.waqiti.recurringpayment.service.RecurringPaymentNotificationService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecurringPaymentExecutionConsumer {
    
    private final RecurringPaymentService recurringPaymentService;
    private final RecurringPaymentSchedulerService recurringPaymentSchedulerService;
    private final RecurringPaymentNotificationService recurringPaymentNotificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"recurring-payment-execution", "recurring-payment-executed"},
        groupId = "recurring-payment-service-execution-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional
    public void handleRecurringPaymentExecution(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("RECURRING PAYMENT EXECUTION: Processing execution - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        UUID executionId = null;
        UUID recurringPaymentId = null;
        String executionStatus = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            executionId = UUID.fromString((String) event.get("executionId"));
            recurringPaymentId = UUID.fromString((String) event.get("recurringPaymentId"));
            UUID userId = UUID.fromString((String) event.get("userId"));
            executionStatus = (String) event.get("executionStatus");
            BigDecimal amount = new BigDecimal(event.get("amount").toString());
            String currency = (String) event.get("currency");
            String paymentMethod = (String) event.get("paymentMethod");
            String recipientAccountId = (String) event.get("recipientAccountId");
            String recipientName = (String) event.get("recipientName");
            LocalDate scheduledDate = LocalDate.parse((String) event.get("scheduledDate"));
            LocalDateTime executionTimestamp = LocalDateTime.parse((String) event.get("executionTimestamp"));
            String frequency = (String) event.get("frequency");
            Integer occurrenceNumber = (Integer) event.get("occurrenceNumber");
            Integer totalOccurrences = event.containsKey("totalOccurrences") ? 
                    (Integer) event.get("totalOccurrences") : null;
            String failureReason = (String) event.get("failureReason");
            Integer retryCount = event.containsKey("retryCount") ? (Integer) event.get("retryCount") : 0;
            Boolean isRetry = (Boolean) event.getOrDefault("isRetry", false);
            LocalDate nextScheduledDate = event.containsKey("nextScheduledDate") ? 
                    LocalDate.parse((String) event.get("nextScheduledDate")) : null;
            UUID transactionId = event.containsKey("transactionId") ? 
                    UUID.fromString((String) event.get("transactionId")) : null;
            
            log.info("Recurring payment execution - ExecutionId: {}, RecurringPaymentId: {}, Status: {}, Amount: {} {}, Occurrence: {}/{}, Frequency: {}", 
                    executionId, recurringPaymentId, executionStatus, amount, currency, 
                    occurrenceNumber, totalOccurrences != null ? totalOccurrences : "∞", frequency);
            
            validateRecurringPaymentExecution(executionId, recurringPaymentId, userId, 
                    executionStatus, amount, scheduledDate);
            
            processExecutionByStatus(executionId, recurringPaymentId, userId, executionStatus, 
                    amount, currency, paymentMethod, recipientAccountId, recipientName, 
                    scheduledDate, executionTimestamp, frequency, occurrenceNumber, 
                    totalOccurrences, failureReason, retryCount, isRetry, nextScheduledDate, 
                    transactionId);
            
            if ("SUCCESS".equals(executionStatus)) {
                handleSuccessfulExecution(executionId, recurringPaymentId, userId, amount, currency, 
                        recipientAccountId, recipientName, scheduledDate, occurrenceNumber, 
                        totalOccurrences, transactionId);
            } else if ("FAILED".equals(executionStatus)) {
                handleFailedExecution(executionId, recurringPaymentId, userId, amount, currency, 
                        failureReason, retryCount, isRetry);
            } else if ("SKIPPED".equals(executionStatus)) {
                handleSkippedExecution(executionId, recurringPaymentId, userId, scheduledDate, 
                        failureReason);
            }
            
            if (nextScheduledDate != null && !"CANCELLED".equals(executionStatus)) {
                scheduleNextExecution(recurringPaymentId, userId, nextScheduledDate, amount, 
                        currency, paymentMethod, recipientAccountId, frequency, occurrenceNumber + 1, 
                        totalOccurrences);
            }
            
            if (totalOccurrences != null && occurrenceNumber >= totalOccurrences) {
                completeRecurringPayment(recurringPaymentId, userId, occurrenceNumber);
            }
            
            notifyUser(userId, executionId, recurringPaymentId, executionStatus, amount, currency, 
                    recipientName, scheduledDate, occurrenceNumber, totalOccurrences);
            
            updateRecurringPaymentMetrics(executionStatus, frequency, amount, paymentMethod, 
                    isRetry);
            
            auditRecurringPaymentExecution(executionId, recurringPaymentId, userId, executionStatus, 
                    amount, currency, occurrenceNumber, processingStartTime);
            
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            
            log.info("Recurring payment execution processed - ExecutionId: {}, Status: {}, ProcessingTime: {}ms", 
                    executionId, executionStatus, processingTimeMs);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Recurring payment execution processing failed - ExecutionId: {}, RecurringPaymentId: {}, Status: {}, Error: {}", 
                    executionId, recurringPaymentId, executionStatus, e.getMessage(), e);
            
            if (executionId != null && recurringPaymentId != null) {
                handleExecutionFailure(executionId, recurringPaymentId, executionStatus, e);
            }
            
            throw new RuntimeException("Recurring payment execution processing failed", e);
        }
    }
    
    private void validateRecurringPaymentExecution(UUID executionId, UUID recurringPaymentId,
                                                   UUID userId, String executionStatus,
                                                   BigDecimal amount, LocalDate scheduledDate) {
        if (executionId == null || recurringPaymentId == null || userId == null) {
            throw new IllegalArgumentException("Execution ID, Recurring Payment ID, and User ID are required");
        }
        
        if (executionStatus == null || executionStatus.trim().isEmpty()) {
            throw new IllegalArgumentException("Execution status is required");
        }
        
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid payment amount");
        }
        
        if (scheduledDate == null) {
            throw new IllegalArgumentException("Scheduled date is required");
        }
        
        log.debug("Recurring payment execution validation passed - ExecutionId: {}", executionId);
    }
    
    private void processExecutionByStatus(UUID executionId, UUID recurringPaymentId, UUID userId,
                                         String executionStatus, BigDecimal amount, String currency,
                                         String paymentMethod, String recipientAccountId,
                                         String recipientName, LocalDate scheduledDate,
                                         LocalDateTime executionTimestamp, String frequency,
                                         Integer occurrenceNumber, Integer totalOccurrences,
                                         String failureReason, Integer retryCount, Boolean isRetry,
                                         LocalDate nextScheduledDate, UUID transactionId) {
        try {
            recurringPaymentService.processExecution(executionId, recurringPaymentId, userId, 
                    executionStatus, amount, currency, paymentMethod, recipientAccountId, 
                    recipientName, scheduledDate, executionTimestamp, frequency, occurrenceNumber, 
                    totalOccurrences, failureReason, retryCount, isRetry, nextScheduledDate, 
                    transactionId);
            
            log.debug("Execution status processing completed - ExecutionId: {}, Status: {}", 
                    executionId, executionStatus);
            
        } catch (Exception e) {
            log.error("Failed to process execution by status - ExecutionId: {}, Status: {}", 
                    executionId, executionStatus, e);
            throw new RuntimeException("Execution status processing failed", e);
        }
    }
    
    private void handleSuccessfulExecution(UUID executionId, UUID recurringPaymentId, UUID userId,
                                          BigDecimal amount, String currency, String recipientAccountId,
                                          String recipientName, LocalDate scheduledDate,
                                          Integer occurrenceNumber, Integer totalOccurrences,
                                          UUID transactionId) {
        try {
            log.info("Processing successful recurring payment execution - ExecutionId: {}, Amount: {} {}, Recipient: {}, Occurrence: {}/{}", 
                    executionId, amount, currency, recipientName, occurrenceNumber, 
                    totalOccurrences != null ? totalOccurrences : "∞");
            
            recurringPaymentService.recordSuccessfulExecution(executionId, recurringPaymentId, 
                    userId, amount, currency, recipientAccountId, recipientName, scheduledDate, 
                    occurrenceNumber, transactionId);
            
            recurringPaymentService.resetRetryCount(recurringPaymentId);
            
        } catch (Exception e) {
            log.error("Failed to handle successful execution - ExecutionId: {}", executionId, e);
        }
    }
    
    private void handleFailedExecution(UUID executionId, UUID recurringPaymentId, UUID userId,
                                      BigDecimal amount, String currency, String failureReason,
                                      Integer retryCount, Boolean isRetry) {
        try {
            log.error("Processing failed recurring payment execution - ExecutionId: {}, Reason: {}, RetryCount: {}, IsRetry: {}", 
                    executionId, failureReason, retryCount, isRetry);
            
            recurringPaymentService.recordFailedExecution(executionId, recurringPaymentId, userId, 
                    amount, currency, failureReason, retryCount);
            
            if (retryCount < 3) {
                recurringPaymentSchedulerService.scheduleRetry(recurringPaymentId, executionId, 
                        userId, amount, currency, retryCount + 1);
                
                log.info("Retry scheduled - RecurringPaymentId: {}, RetryAttempt: {}", 
                        recurringPaymentId, retryCount + 1);
            } else {
                log.error("Max retry attempts reached - RecurringPaymentId: {}, Suspending recurring payment", 
                        recurringPaymentId);
                
                recurringPaymentService.suspendRecurringPayment(recurringPaymentId, userId, 
                        "Max retry attempts exceeded: " + failureReason);
            }
            
        } catch (Exception e) {
            log.error("Failed to handle failed execution - ExecutionId: {}", executionId, e);
        }
    }
    
    private void handleSkippedExecution(UUID executionId, UUID recurringPaymentId, UUID userId,
                                       LocalDate scheduledDate, String failureReason) {
        try {
            log.warn("Processing skipped recurring payment execution - ExecutionId: {}, ScheduledDate: {}, Reason: {}", 
                    executionId, scheduledDate, failureReason);
            
            recurringPaymentService.recordSkippedExecution(executionId, recurringPaymentId, userId, 
                    scheduledDate, failureReason);
            
        } catch (Exception e) {
            log.error("Failed to handle skipped execution - ExecutionId: {}", executionId, e);
        }
    }
    
    private void scheduleNextExecution(UUID recurringPaymentId, UUID userId, LocalDate nextScheduledDate,
                                      BigDecimal amount, String currency, String paymentMethod,
                                      String recipientAccountId, String frequency,
                                      Integer nextOccurrenceNumber, Integer totalOccurrences) {
        try {
            log.info("Scheduling next recurring payment execution - RecurringPaymentId: {}, NextDate: {}, Occurrence: {}/{}", 
                    recurringPaymentId, nextScheduledDate, nextOccurrenceNumber, 
                    totalOccurrences != null ? totalOccurrences : "∞");
            
            recurringPaymentSchedulerService.scheduleNextExecution(recurringPaymentId, userId, 
                    nextScheduledDate, amount, currency, paymentMethod, recipientAccountId, 
                    frequency, nextOccurrenceNumber, totalOccurrences);
            
        } catch (Exception e) {
            log.error("Failed to schedule next execution - RecurringPaymentId: {}", 
                    recurringPaymentId, e);
        }
    }
    
    private void completeRecurringPayment(UUID recurringPaymentId, UUID userId, 
                                         Integer finalOccurrenceNumber) {
        try {
            log.info("Completing recurring payment - RecurringPaymentId: {}, TotalOccurrences: {}", 
                    recurringPaymentId, finalOccurrenceNumber);
            
            recurringPaymentService.completeRecurringPayment(recurringPaymentId, userId, 
                    finalOccurrenceNumber);
            
        } catch (Exception e) {
            log.error("Failed to complete recurring payment - RecurringPaymentId: {}", 
                    recurringPaymentId, e);
        }
    }
    
    private void notifyUser(UUID userId, UUID executionId, UUID recurringPaymentId,
                           String executionStatus, BigDecimal amount, String currency,
                           String recipientName, LocalDate scheduledDate, Integer occurrenceNumber,
                           Integer totalOccurrences) {
        try {
            recurringPaymentNotificationService.sendExecutionNotification(userId, executionId, 
                    recurringPaymentId, executionStatus, amount, currency, recipientName, 
                    scheduledDate, occurrenceNumber, totalOccurrences);
            
            log.info("User notified of recurring payment execution - UserId: {}, ExecutionId: {}, Status: {}", 
                    userId, executionId, executionStatus);
            
        } catch (Exception e) {
            log.error("Failed to notify user - UserId: {}, ExecutionId: {}", userId, executionId, e);
        }
    }
    
    private void updateRecurringPaymentMetrics(String executionStatus, String frequency,
                                              BigDecimal amount, String paymentMethod, Boolean isRetry) {
        try {
            recurringPaymentService.updateExecutionMetrics(executionStatus, frequency, amount, 
                    paymentMethod, isRetry);
        } catch (Exception e) {
            log.error("Failed to update recurring payment metrics - Status: {}", executionStatus, e);
        }
    }
    
    private void auditRecurringPaymentExecution(UUID executionId, UUID recurringPaymentId, UUID userId,
                                               String executionStatus, BigDecimal amount, String currency,
                                               Integer occurrenceNumber, LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditFinancialEvent(
                    "RECURRING_PAYMENT_EXECUTION_PROCESSED",
                    userId.toString(),
                    String.format("Recurring payment execution %s - Occurrence: %d, Amount: %s %s", 
                            executionStatus, occurrenceNumber, amount, currency),
                    Map.of(
                            "executionId", executionId.toString(),
                            "recurringPaymentId", recurringPaymentId.toString(),
                            "userId", userId.toString(),
                            "executionStatus", executionStatus,
                            "amount", amount.toString(),
                            "currency", currency,
                            "occurrenceNumber", occurrenceNumber,
                            "processingTimeMs", processingTimeMs
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit recurring payment execution - ExecutionId: {}", executionId, e);
        }
    }
    
    private void handleExecutionFailure(UUID executionId, UUID recurringPaymentId,
                                       String executionStatus, Exception error) {
        try {
            recurringPaymentService.handleExecutionFailure(executionId, recurringPaymentId, 
                    executionStatus, error.getMessage());
            
            auditService.auditFinancialEvent(
                    "RECURRING_PAYMENT_EXECUTION_PROCESSING_FAILED",
                    "SYSTEM",
                    "Failed to process recurring payment execution: " + error.getMessage(),
                    Map.of(
                            "executionId", executionId.toString(),
                            "recurringPaymentId", recurringPaymentId.toString(),
                            "executionStatus", executionStatus != null ? executionStatus : "UNKNOWN",
                            "error", error.getClass().getSimpleName(),
                            "errorMessage", error.getMessage()
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to handle execution failure - ExecutionId: {}", executionId, e);
        }
    }
    
    @KafkaListener(
        topics = {"recurring-payment-execution.DLQ", "recurring-payment-executed.DLQ"},
        groupId = "recurring-payment-service-execution-dlq-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlq(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "x-original-topic", required = false) String originalTopic,
            @Header(value = "x-error-message", required = false) String errorMessage,
            @Header(value = "x-error-class", required = false) String errorClass,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        log.error("CRITICAL: Recurring payment execution event sent to DLQ - OriginalTopic: {}, Error: {}, ErrorClass: {}, Event: {}", 
                originalTopic, errorMessage, errorClass, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            UUID executionId = event.containsKey("executionId") ? 
                    UUID.fromString((String) event.get("executionId")) : null;
            UUID recurringPaymentId = event.containsKey("recurringPaymentId") ? 
                    UUID.fromString((String) event.get("recurringPaymentId")) : null;
            UUID userId = event.containsKey("userId") ? 
                    UUID.fromString((String) event.get("userId")) : null;
            String executionStatus = (String) event.get("executionStatus");
            
            log.error("DLQ: Recurring payment execution failed permanently - ExecutionId: {}, RecurringPaymentId: {}, UserId: {}, Status: {} - MANUAL INTERVENTION REQUIRED", 
                    executionId, recurringPaymentId, userId, executionStatus);
            
            if (executionId != null && recurringPaymentId != null) {
                recurringPaymentService.markForManualReview(executionId, recurringPaymentId, userId, 
                        executionStatus, "DLQ: " + errorMessage);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse recurring payment execution DLQ event: {}", eventJson, e);
        }
    }
}