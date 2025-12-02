package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.payment.service.PaymentService;
import com.waqiti.payment.service.TransactionService;
import com.waqiti.payment.service.NotificationService;
import com.waqiti.payment.domain.Transaction;
import com.waqiti.payment.domain.TransactionStatus;
import com.waqiti.payment.repository.TransactionRepository;
import com.waqiti.common.distributed.DistributedLockService;
import com.waqiti.common.security.audit.SecurityAuditLogger;
import com.waqiti.common.messaging.deadletter.DeadLetterQueueService;
import com.waqiti.common.messaging.deadletter.DeadLetterMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * CRITICAL: Transaction Control Consumer - Processes orphaned transaction control events
 * 
 * This consumer was missing causing transaction unblocks and saga lifecycle events to be lost.
 * Without this, blocked transactions would never be unblocked and saga transactions would remain incomplete.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionControlConsumer {

    private final PaymentService paymentService;
    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final NotificationService notificationService;
    private final DistributedLockService lockService;
    private final SecurityAuditLogger securityAuditLogger;
    private final ObjectMapper objectMapper;
    private final DeadLetterQueueService deadLetterQueueService;

    @KafkaListener(
        topics = {"transaction-unblocks", "transaction-resumes", "transaction-abort", 
                 "transaction-commit", "transaction-prepare", "transaction-rollback"},
        groupId = "transaction-control-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void processTransactionControlEvent(
            @NonNull @Payload String eventPayload,
            @NonNull @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @NonNull Acknowledgment acknowledgment) {

        String lockKey = null;
        
        try {
            log.info("Processing transaction control event from topic: {} - partition: {} - offset: {}", 
                    topic, partition, offset);

            // Parse event payload
            Map<String, Object> eventData = objectMapper.readValue(eventPayload, Map.class);
            String transactionId = extractString(eventData, "transactionId");
            String sagaId = extractString(eventData, "sagaId");
            String userId = extractString(eventData, "userId");
            String eventType = determineEventTypeFromTopic(topic);

            // Validate required fields
            if (transactionId == null && sagaId == null) {
                log.error("Invalid transaction control event - missing transactionId and sagaId");
                acknowledgment.acknowledge(); // Ack to prevent reprocessing
                return;
            }

            // Use transactionId or sagaId for locking
            String lockId = transactionId != null ? transactionId : sagaId;
            lockKey = "transaction-control-" + lockId;
            
            boolean lockAcquired = lockService.tryLock(lockKey, 60, TimeUnit.SECONDS);
            if (!lockAcquired) {
                log.warn("Could not acquire lock for transaction control: {}", lockId);
                throw new RuntimeException("Lock acquisition failed");
            }

            try {
                // Process based on event type
                switch (eventType.toUpperCase()) {
                    case "UNBLOCK":
                        processTransactionUnblock(transactionId, userId, eventData);
                        break;
                    case "RESUME":
                        processTransactionResume(transactionId, userId, eventData);
                        break;
                    case "PREPARE":
                        processSagaPrepare(sagaId, transactionId, eventData);
                        break;
                    case "COMMIT":
                        processSagaCommit(sagaId, transactionId, eventData);
                        break;
                    case "ABORT":
                        processSagaAbort(sagaId, transactionId, eventData);
                        break;
                    case "ROLLBACK":
                        processSagaRollback(sagaId, transactionId, eventData);
                        break;
                    default:
                        log.warn("Unknown transaction control event type: {}", eventType);
                        processUnknownTransactionEvent(lockId, eventType, eventData);
                }
                
                acknowledgment.acknowledge();
                
            } finally {
                lockService.unlock(lockKey);
            }
            
        } catch (Exception e) {
            log.error("Error processing transaction control event", e);
            
            // Send to DLQ after max retries
            if (shouldSendToDlq(e)) {
                sendToDlq(eventPayload, topic, e);
                acknowledgment.acknowledge(); // Acknowledge to prevent reprocessing
            } else {
                throw e; // Let retry mechanism handle
            }
        }
    }

    /**
     * Process transaction unblock event - unblock transactions blocked by OFAC/sanctions
     */
    private void processTransactionUnblock(@NonNull String transactionId, @Nullable String userId, @NonNull Map<String, Object> eventData) {
        
        log.info("Processing transaction unblock - transactionId: {} userId: {}", transactionId, userId);
        
        try {
            // Get transaction record
            Transaction transaction = getTransaction(transactionId);
            
            // Validate transaction is in blocked state
            if (transaction.getStatus() != TransactionStatus.BLOCKED) {
                log.warn("Transaction {} not in BLOCKED state - current status: {}", 
                    transactionId, transaction.getStatus());
                return;
            }
            
            // Extract unblock details
            String unblockReason = extractString(eventData, "unblockReason");
            String unblockAuthority = extractString(eventData, "unblockAuthority");
            String complianceReference = extractString(eventData, "complianceReference");
            String reviewedBy = extractString(eventData, "reviewedBy");
            
            // Update transaction status
            transaction.setStatus(TransactionStatus.PENDING);
            transaction.setUnblockedAt(LocalDateTime.now());
            transaction.setUnblockReason(unblockReason);
            transaction.setUnblockAuthority(unblockAuthority);
            transaction.setComplianceReference(complianceReference);
            transaction.setReviewedBy(reviewedBy);
            
            transactionRepository.save(transaction);
            
            // Resume transaction processing
            resumeTransactionProcessing(transaction);
            
            // Send unblock notifications
            sendUnblockNotifications(transaction, unblockReason, reviewedBy);
            
            // Log unblock event
            securityAuditLogger.logSecurityEvent("TRANSACTION_UNBLOCKED", 
                reviewedBy != null ? reviewedBy : "SYSTEM",
                "Transaction unblocked and resumed",
                Map.of("transactionId", transactionId, "amount", transaction.getAmount(),
                      "unblockReason", unblockReason != null ? unblockReason : "N/A",
                      "unblockAuthority", unblockAuthority != null ? unblockAuthority : "N/A",
                      "complianceReference", complianceReference != null ? complianceReference : "N/A"));
            
            log.info("Successfully unblocked transaction: {} - reason: {}", transactionId, unblockReason);
                
        } catch (Exception e) {
            log.error("Failed to process transaction unblock for transaction: {}", transactionId, e);
            throw e;
        }
    }

    /**
     * Process transaction resume event - resume paused transactions
     */
    private void processTransactionResume(@NonNull String transactionId, @Nullable String userId, @NonNull Map<String, Object> eventData) {
        
        log.info("Processing transaction resume - transactionId: {} userId: {}", transactionId, userId);
        
        try {
            // Get transaction record
            Transaction transaction = getTransaction(transactionId);
            
            // Validate transaction can be resumed
            if (transaction.getStatus() != TransactionStatus.PAUSED && 
                transaction.getStatus() != TransactionStatus.PENDING_REVIEW) {
                log.warn("Transaction {} cannot be resumed - current status: {}", 
                    transactionId, transaction.getStatus());
                return;
            }
            
            // Extract resume details
            String resumeReason = extractString(eventData, "resumeReason");
            String resumedBy = extractString(eventData, "resumedBy");
            String reviewNotes = extractString(eventData, "reviewNotes");
            
            // Update transaction status
            transaction.setStatus(TransactionStatus.PENDING);
            transaction.setResumedAt(LocalDateTime.now());
            transaction.setResumeReason(resumeReason);
            transaction.setResumedBy(resumedBy);
            transaction.setReviewNotes(reviewNotes);
            
            transactionRepository.save(transaction);
            
            // Resume transaction processing
            resumeTransactionProcessing(transaction);
            
            // Send resume notifications
            sendResumeNotifications(transaction, resumeReason, resumedBy);
            
            // Log resume event
            securityAuditLogger.logSecurityEvent("TRANSACTION_RESUMED", 
                resumedBy != null ? resumedBy : "SYSTEM",
                "Transaction resumed",
                Map.of("transactionId", transactionId, "amount", transaction.getAmount(),
                      "resumeReason", resumeReason != null ? resumeReason : "N/A",
                      "resumedBy", resumedBy != null ? resumedBy : "SYSTEM"));
            
            log.info("Successfully resumed transaction: {} - reason: {}", transactionId, resumeReason);
                
        } catch (Exception e) {
            log.error("Failed to process transaction resume for transaction: {}", transactionId, e);
            throw e;
        }
    }

    /**
     * Process saga prepare event - prepare phase of distributed transaction
     */
    private void processSagaPrepare(@NonNull String sagaId, @Nullable String transactionId, @NonNull Map<String, Object> eventData) {
        
        log.info("Processing saga prepare - sagaId: {} transactionId: {}", sagaId, transactionId);
        
        try {
            // Extract prepare details
            String participantId = extractString(eventData, "participantId");
            String operation = extractString(eventData, "operation");
            BigDecimal amount = extractBigDecimal(eventData, "amount");
            String resourceId = extractString(eventData, "resourceId");
            
            // Execute prepare phase
            boolean prepareSuccessful = executeSagaPrepare(sagaId, transactionId, participantId, operation, amount, resourceId);
            
            if (prepareSuccessful) {
                // Send prepare success response
                sendSagaResponse(sagaId, transactionId, participantId, "PREPARED", null);
                
                log.info("Successfully prepared saga transaction: {} - participant: {}", sagaId, participantId);
            } else {
                // Send prepare failure response
                sendSagaResponse(sagaId, transactionId, participantId, "PREPARE_FAILED", "Resource unavailable or locked");
                
                log.error("Failed to prepare saga transaction: {} - participant: {}", sagaId, participantId);
            }
            
            // Log prepare event
            securityAuditLogger.logSecurityEvent("SAGA_PREPARE_PROCESSED", "SYSTEM",
                "Saga prepare phase processed",
                Map.of("sagaId", sagaId, "transactionId", transactionId != null ? transactionId : "N/A",
                      "participantId", participantId != null ? participantId : "N/A",
                      "operation", operation != null ? operation : "N/A",
                      "result", prepareSuccessful ? "SUCCESS" : "FAILED"));
                
        } catch (Exception e) {
            log.error("Failed to process saga prepare for saga: {}", sagaId, e);
            
            // Send prepare failure response
            try {
                String participantId = extractString(eventData, "participantId");
                sendSagaResponse(sagaId, transactionId, participantId, "PREPARE_FAILED", e.getMessage());
            } catch (Exception responseError) {
                log.error("Failed to send prepare failure response", responseError);
            }
            
            throw e;
        }
    }

    /**
     * Process saga commit event - commit phase of distributed transaction
     */
    private void processSagaCommit(String sagaId, String transactionId, Map<String, Object> eventData) {
        
        log.info("Processing saga commit - sagaId: {} transactionId: {}", sagaId, transactionId);
        
        try {
            // Extract commit details
            String participantId = extractString(eventData, "participantId");
            String operation = extractString(eventData, "operation");
            
            // Execute commit phase
            boolean commitSuccessful = executeSagaCommit(sagaId, transactionId, participantId, operation);
            
            if (commitSuccessful) {
                // Send commit success response
                sendSagaResponse(sagaId, transactionId, participantId, "COMMITTED", null);
                
                log.info("Successfully committed saga transaction: {} - participant: {}", sagaId, participantId);
            } else {
                // Send commit failure response (this is a serious issue)
                sendSagaResponse(sagaId, transactionId, participantId, "COMMIT_FAILED", "Commit operation failed");
                
                // Alert operations immediately
                sendCriticalAlert("SAGA_COMMIT_FAILED", sagaId, "Saga commit failed - manual intervention required");
                
                log.error("CRITICAL: Failed to commit saga transaction: {} - participant: {}", sagaId, participantId);
            }
            
            // Log commit event
            securityAuditLogger.logSecurityEvent("SAGA_COMMIT_PROCESSED", "SYSTEM",
                "Saga commit phase processed",
                Map.of("sagaId", sagaId, "transactionId", transactionId != null ? transactionId : "N/A",
                      "participantId", participantId != null ? participantId : "N/A",
                      "operation", operation != null ? operation : "N/A",
                      "result", commitSuccessful ? "SUCCESS" : "FAILED"));
                
        } catch (Exception e) {
            log.error("Failed to process saga commit for saga: {}", sagaId, e);
            
            // Send commit failure response
            try {
                String participantId = extractString(eventData, "participantId");
                sendSagaResponse(sagaId, transactionId, participantId, "COMMIT_FAILED", e.getMessage());
                
                // Critical alert
                sendCriticalAlert("SAGA_COMMIT_ERROR", sagaId, "Saga commit error: " + e.getMessage());
            } catch (Exception responseError) {
                log.error("Failed to send commit failure response", responseError);
            }
            
            throw e;
        }
    }

    /**
     * Process saga abort event - abort distributed transaction
     */
    private void processSagaAbort(String sagaId, String transactionId, Map<String, Object> eventData) {
        
        log.info("Processing saga abort - sagaId: {} transactionId: {}", sagaId, transactionId);
        
        try {
            // Extract abort details
            String participantId = extractString(eventData, "participantId");
            String abortReason = extractString(eventData, "abortReason");
            
            // Execute abort phase
            boolean abortSuccessful = executeSagaAbort(sagaId, transactionId, participantId, abortReason);
            
            if (abortSuccessful) {
                // Send abort success response
                sendSagaResponse(sagaId, transactionId, participantId, "ABORTED", null);
                
                log.info("Successfully aborted saga transaction: {} - participant: {} - reason: {}", 
                    sagaId, participantId, abortReason);
            } else {
                // Send abort failure response
                sendSagaResponse(sagaId, transactionId, participantId, "ABORT_FAILED", "Abort operation failed");
                
                log.error("Failed to abort saga transaction: {} - participant: {}", sagaId, participantId);
            }
            
            // Log abort event
            securityAuditLogger.logSecurityEvent("SAGA_ABORT_PROCESSED", "SYSTEM",
                "Saga abort phase processed",
                Map.of("sagaId", sagaId, "transactionId", transactionId != null ? transactionId : "N/A",
                      "participantId", participantId != null ? participantId : "N/A",
                      "abortReason", abortReason != null ? abortReason : "N/A",
                      "result", abortSuccessful ? "SUCCESS" : "FAILED"));
                
        } catch (Exception e) {
            log.error("Failed to process saga abort for saga: {}", sagaId, e);
            throw e;
        }
    }

    /**
     * Process saga rollback event - rollback prepared changes
     */
    private void processSagaRollback(String sagaId, String transactionId, Map<String, Object> eventData) {
        
        log.info("Processing saga rollback - sagaId: {} transactionId: {}", sagaId, transactionId);
        
        try {
            // Extract rollback details
            String participantId = extractString(eventData, "participantId");
            String rollbackReason = extractString(eventData, "rollbackReason");
            
            // Execute rollback phase
            boolean rollbackSuccessful = executeSagaRollback(sagaId, transactionId, participantId, rollbackReason);
            
            if (rollbackSuccessful) {
                // Send rollback success response
                sendSagaResponse(sagaId, transactionId, participantId, "ROLLED_BACK", null);
                
                log.info("Successfully rolled back saga transaction: {} - participant: {} - reason: {}", 
                    sagaId, participantId, rollbackReason);
            } else {
                // Send rollback failure response (this is critical)
                sendSagaResponse(sagaId, transactionId, participantId, "ROLLBACK_FAILED", "Rollback operation failed");
                
                // Critical alert
                sendCriticalAlert("SAGA_ROLLBACK_FAILED", sagaId, "Saga rollback failed - manual intervention required");
                
                log.error("CRITICAL: Failed to rollback saga transaction: {} - participant: {}", sagaId, participantId);
            }
            
            // Log rollback event
            securityAuditLogger.logSecurityEvent("SAGA_ROLLBACK_PROCESSED", "SYSTEM",
                "Saga rollback phase processed",
                Map.of("sagaId", sagaId, "transactionId", transactionId != null ? transactionId : "N/A",
                      "participantId", participantId != null ? participantId : "N/A",
                      "rollbackReason", rollbackReason != null ? rollbackReason : "N/A",
                      "result", rollbackSuccessful ? "SUCCESS" : "FAILED"));
                
        } catch (Exception e) {
            log.error("Failed to process saga rollback for saga: {}", sagaId, e);
            throw e;
        }
    }

    /**
     * Process unknown transaction control event
     */
    private void processUnknownTransactionEvent(String transactionId, String eventType, Map<String, Object> eventData) {
        
        log.warn("Processing unknown transaction control event type: {} for transaction: {}", eventType, transactionId);
        
        try {
            // Queue for manual review
            queueUnknownEventForReview(transactionId, eventType, eventData);
            
            // Send alert to operations
            sendOperationsAlert("UNKNOWN_TRANSACTION_CONTROL_EVENT", transactionId, 
                "Unknown transaction control event type received: " + eventType);
            
        } catch (Exception e) {
            log.error("Failed to process unknown transaction control event: {}", transactionId, e);
        }
    }

    /**
     * Get transaction record
     */
    @NonNull
    private Transaction getTransaction(@NonNull String transactionId) {
        return transactionRepository.findById(UUID.fromString(transactionId))
            .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));
    }

    /**
     * Determine event type from topic
     */
    @NonNull
    private String determineEventTypeFromTopic(@NonNull String topic) {
        if (topic.contains("unblock")) {
            return "UNBLOCK";
        } else if (topic.contains("resume")) {
            return "RESUME";
        } else if (topic.contains("prepare")) {
            return "PREPARE";
        } else if (topic.contains("commit")) {
            return "COMMIT";
        } else if (topic.contains("abort")) {
            return "ABORT";
        } else if (topic.contains("rollback")) {
            return "ROLLBACK";
        } else {
            return "UNKNOWN";
        }
    }

    /**
     * Resume transaction processing
     */
    private void resumeTransactionProcessing(Transaction transaction) {
        try {
            // Use transaction service to resume processing
            transactionService.resumeTransaction(transaction.getId().toString());
        } catch (Exception e) {
            log.error("Failed to resume transaction processing: {}", transaction.getId(), e);
        }
    }

    /**
     * Execute saga prepare phase
     */
    private boolean executeSagaPrepare(String sagaId, String transactionId, String participantId, 
                                     String operation, BigDecimal amount, String resourceId) {
        try {
            // Implementation depends on the specific operation
            switch (operation != null ? operation.toUpperCase() : "UNKNOWN") {
                case "RESERVE_FUNDS":
                    return paymentService.reserveFunds(resourceId, amount);
                case "LOCK_ACCOUNT":
                    return paymentService.lockAccount(resourceId);
                case "VALIDATE_PAYMENT":
                    return paymentService.validatePayment(transactionId, amount);
                default:
                    log.warn("Unknown saga operation: {}", operation);
                    return false;
            }
        } catch (Exception e) {
            log.error("Failed to execute saga prepare: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Execute saga commit phase
     */
    private boolean executeSagaCommit(String sagaId, String transactionId, String participantId, String operation) {
        try {
            // Implementation depends on the specific operation
            switch (operation != null ? operation.toUpperCase() : "UNKNOWN") {
                case "TRANSFER_FUNDS":
                    return paymentService.transferFunds(transactionId);
                case "UNLOCK_ACCOUNT":
                    return paymentService.unlockAccount(participantId);
                case "COMPLETE_PAYMENT":
                    return paymentService.completePayment(transactionId);
                default:
                    log.warn("Unknown saga commit operation: {}", operation);
                    return false;
            }
        } catch (Exception e) {
            log.error("Failed to execute saga commit: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Execute saga abort phase
     */
    private boolean executeSagaAbort(String sagaId, String transactionId, String participantId, String abortReason) {
        try {
            // Release any resources and revert changes
            paymentService.abortTransaction(transactionId, abortReason);
            return true;
        } catch (Exception e) {
            log.error("Failed to execute saga abort: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Execute saga rollback phase
     */
    private boolean executeSagaRollback(String sagaId, String transactionId, String participantId, String rollbackReason) {
        try {
            // Rollback prepared changes
            paymentService.rollbackTransaction(transactionId, rollbackReason);
            return true;
        } catch (Exception e) {
            log.error("Failed to execute saga rollback: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Send saga response
     */
    private void sendSagaResponse(String sagaId, String transactionId, String participantId, String status, String error) {
        try {
            Map<String, Object> response = Map.of(
                "sagaId", sagaId,
                "transactionId", transactionId != null ? transactionId : "",
                "participantId", participantId != null ? participantId : "",
                "status", status,
                "error", error != null ? error : "",
                "timestamp", LocalDateTime.now().toString()
            );
            
            // Send to saga coordinator
            paymentService.sendSagaResponse("saga-responses", response);
            
        } catch (Exception e) {
            log.error("Failed to send saga response", e);
        }
    }

    /**
     * Send critical alert for saga failures
     */
    private void sendCriticalAlert(String alertType, String sagaId, String message) {
        try {
            notificationService.sendCriticalAlert(alertType, message,
                Map.of("sagaId", sagaId, "timestamp", LocalDateTime.now().toString(), "severity", "CRITICAL"));
        } catch (Exception e) {
            log.error("Failed to send critical alert", e);
        }
    }

    /**
     * Queue unknown event for review
     */
    private void queueUnknownEventForReview(String transactionId, String eventType, Map<String, Object> eventData) {
        try {
            Map<String, Object> reviewTask = Map.of(
                "taskType", "UNKNOWN_TRANSACTION_CONTROL_EVENT_REVIEW",
                "transactionId", transactionId,
                "eventType", eventType,
                "eventData", eventData,
                "priority", "HIGH",
                "createdAt", LocalDateTime.now().toString()
            );
            
            paymentService.queueManualTask("unknown-event-review-queue", reviewTask);
            
        } catch (Exception e) {
            log.error("Failed to queue unknown event for review: {}", transactionId, e);
        }
    }

    /**
     * Send various notification types
     */
    private void sendUnblockNotifications(Transaction transaction, String unblockReason, String reviewedBy) {
        try {
            notificationService.sendCustomerNotification(
                transaction.getUserId(),
                "TRANSACTION_UNBLOCKED",
                "Your transaction has been unblocked and is being processed. Reference: " + transaction.getId(),
                Map.of("transactionId", transaction.getId(), "amount", transaction.getAmount(),
                      "unblockReason", unblockReason != null ? unblockReason : "N/A")
            );
        } catch (Exception e) {
            log.error("Failed to send unblock notification", e);
        }
    }

    private void sendResumeNotifications(Transaction transaction, String resumeReason, String resumedBy) {
        try {
            notificationService.sendCustomerNotification(
                transaction.getUserId(),
                "TRANSACTION_RESUMED",
                "Your transaction has been resumed and is being processed. Reference: " + transaction.getId(),
                Map.of("transactionId", transaction.getId(), "amount", transaction.getAmount(),
                      "resumeReason", resumeReason != null ? resumeReason : "N/A")
            );
        } catch (Exception e) {
            log.error("Failed to send resume notification", e);
        }
    }

    private void sendOperationsAlert(String alertType, String transactionId, String message) {
        try {
            notificationService.sendOperationsAlert(alertType, message,
                Map.of("transactionId", transactionId, "timestamp", LocalDateTime.now().toString()));
        } catch (Exception e) {
            log.error("Failed to send operations alert", e);
        }
    }

    /**
     * Helper methods for data extraction
     */
    @Nullable
    private String extractString(@NonNull Map<String, Object> map, @NonNull String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    @Nullable
    private BigDecimal extractBigDecimal(@NonNull Map<String, Object> map, @NonNull String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse BigDecimal from value: {} for key: {}", value, key);
            return null;
        }
    }

    private boolean shouldSendToDlq(Exception e) {
        // Send to DLQ for non-retryable errors
        return e instanceof IllegalArgumentException ||
               e instanceof IllegalStateException ||
               e instanceof SecurityException;
    }

    private void sendToDlq(String eventPayload, String topic, Exception error) {
        try {
            log.error("CRITICAL: Sending transaction control event to DLQ: {}", error.getMessage());
            
            // Create comprehensive DLQ message
            DeadLetterMessage dlqMessage = DeadLetterMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .originalTopic(topic)
                .originalPayload(eventPayload)
                .failureReason(error.getMessage())
                .errorType(error.getClass().getSimpleName())
                .stackTrace(getStackTrace(error))
                .retryCount(3) // Assuming max retries reached
                .financialMessage(true) // Transaction control is financial
                .priority(DeadLetterMessage.MessagePriority.CRITICAL)
                .category(DeadLetterMessage.MessageCategory.TRANSACTION)
                .originalTimestamp(LocalDateTime.now())
                .manualInterventionRequired(true) // Financial messages need review
                .build();
            
            // Send to DLQ service
            deadLetterQueueService.sendToDeadLetterQueue(dlqMessage)
                .whenComplete((result, dlqError) -> {
                    if (dlqError != null) {
                        log.error("CRITICAL: Failed to send transaction control event to DLQ", dlqError);
                    } else {
                        log.info("SUCCESS: Transaction control event sent to DLQ - MessageId: {}", 
                            dlqMessage.getMessageId());
                    }
                });
            
            // Security audit log for financial message failure
            securityAuditLogger.logSecurityEvent("TRANSACTION_CONTROL_EVENT_DLQ", "SYSTEM",
                "Critical transaction control event sent to DLQ - manual intervention required",
                Map.of("topic", topic, "error", error.getMessage(), "messageId", dlqMessage.getMessageId()));
                
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process DLQ for transaction control event", e);
        }
    }
    
    private String getStackTrace(Exception error) {
        return org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace(error);
    }
}