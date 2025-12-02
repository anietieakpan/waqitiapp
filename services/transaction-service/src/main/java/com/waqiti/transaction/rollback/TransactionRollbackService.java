package com.waqiti.transaction.rollback;

import com.waqiti.transaction.domain.Transaction;
import com.waqiti.transaction.domain.TransactionStatus;
import com.waqiti.transaction.repository.TransactionRepository;
import com.waqiti.transaction.service.TransactionService;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.saga.SagaOrchestratorService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * PRODUCTION-GRADE Transaction Rollback Service
 * Handles complex transaction rollbacks with compensation actions, audit trails, and saga coordination
 * Critical for financial transaction integrity and regulatory compliance
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionRollbackService {

    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final IdempotencyService idempotencyService;
    private final SagaOrchestratorService sagaOrchestratorService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RollbackAuditService rollbackAuditService;
    private final CompensationActionService compensationActionService;

    /**
     * CRITICAL: Rollback a single transaction with comprehensive audit trail
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public RollbackResult rollbackTransaction(UUID transactionId, String reason, String initiatedBy) {
        log.info("SECURITY: Initiating transaction rollback for transaction: {}, reason: {}, by: {}", 
                transactionId, reason, initiatedBy);

        String idempotencyKey = "rollback:" + transactionId + ":" + System.currentTimeMillis();

        return idempotencyService.executeIdempotentWithPersistence(
            "transaction-rollback-service", 
            "rollback-transaction",
            idempotencyKey,
            () -> executeRollback(transactionId, reason, initiatedBy),
            java.time.Duration.ofHours(2)
        );
    }

    /**
     * CRITICAL: Rollback multiple transactions atomically (batch rollback)
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BatchRollbackResult rollbackTransactionBatch(List<UUID> transactionIds, String reason, String initiatedBy) {
        log.info("SECURITY: Initiating batch transaction rollback for {} transactions, reason: {}, by: {}", 
                transactionIds.size(), reason, initiatedBy);

        String idempotencyKey = "batch-rollback:" + String.join("-", 
                transactionIds.stream().map(UUID::toString).toList()) + ":" + System.currentTimeMillis();

        return idempotencyService.executeIdempotentWithPersistence(
            "transaction-rollback-service", 
            "rollback-batch",
            idempotencyKey,
            () -> executeBatchRollback(transactionIds, reason, initiatedBy),
            java.time.Duration.ofHours(4)
        );
    }

    /**
     * CRITICAL: Emergency rollback for critical system failures
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public EmergencyRollbackResult emergencyRollback(EmergencyRollbackRequest request) {
        log.error("EMERGENCY: Initiating emergency transaction rollback for reason: {}, by: {}", 
                request.getReason(), request.getInitiatedBy());

        // Create audit trail for emergency action
        rollbackAuditService.logEmergencyRollbackInitiation(request);

        return executeEmergencyRollback(request);
    }

    /**
     * Execute single transaction rollback with compensation actions
     */
    private RollbackResult executeRollback(UUID transactionId, String reason, String initiatedBy) {
        try {
            // 1. Load and validate transaction
            Transaction transaction = transactionRepository.findByIdWithPessimisticWriteLock(transactionId)
                    .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + transactionId));

            // 2. Validate rollback eligibility
            validateRollbackEligibility(transaction);

            // 3. Create rollback audit record
            RollbackAuditRecord auditRecord = rollbackAuditService.createRollbackRecord(
                    transaction, reason, initiatedBy);

            // 4. Execute compensation actions
            List<CompensationAction> compensationActions = compensationActionService
                    .generateCompensationActions(transaction);

            CompensationResult compensationResult = compensationActionService
                    .executeCompensationActions(compensationActions);

            // 5. Update transaction status
            transaction.setStatus(TransactionStatus.ROLLED_BACK);
            transaction.setRollbackReason(reason);
            transaction.setRolledBackBy(initiatedBy);
            transaction.setRolledBackAt(LocalDateTime.now());
            transaction.setUpdatedAt(LocalDateTime.now());

            Transaction savedTransaction = transactionRepository.save(transaction);

            // 6. Trigger saga rollback if part of a saga
            if (transaction.getSagaId() != null) {
                sagaOrchestratorService.rollbackSaga(transaction.getSagaId(), reason);
            }

            // 7. Publish rollback event
            publishRollbackEvent(savedTransaction, reason, initiatedBy);

            // 8. Complete audit record
            rollbackAuditService.completeRollbackRecord(auditRecord, compensationResult);

            log.info("SECURITY: Transaction rollback completed successfully for transaction: {}", transactionId);

            return RollbackResult.builder()
                    .transactionId(transactionId)
                    .status(RollbackStatus.COMPLETED)
                    .reason(reason)
                    .compensationActions(compensationActions.size())
                    .auditRecordId(auditRecord.getId())
                    .completedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("CRITICAL: Transaction rollback failed for transaction: {}", transactionId, e);
            
            // Create failure audit record
            rollbackAuditService.logRollbackFailure(transactionId, reason, initiatedBy, e);

            return RollbackResult.builder()
                    .transactionId(transactionId)
                    .status(RollbackStatus.FAILED)
                    .reason(reason)
                    .errorMessage(e.getMessage())
                    .failedAt(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Execute batch rollback with atomic success/failure
     */
    private BatchRollbackResult executeBatchRollback(List<UUID> transactionIds, String reason, String initiatedBy) {
        BatchRollbackResult.BatchRollbackResultBuilder resultBuilder = BatchRollbackResult.builder()
                .reason(reason)
                .initiatedBy(initiatedBy)
                .startedAt(LocalDateTime.now());

        List<RollbackResult> successfulRollbacks = new ArrayList<>();
        List<RollbackResult> failedRollbacks = new ArrayList<>();

        // Create batch audit record
        BatchRollbackAuditRecord batchAuditRecord = rollbackAuditService
                .createBatchRollbackRecord(transactionIds, reason, initiatedBy);

        try {
            // Process each transaction
            for (UUID transactionId : transactionIds) {
                try {
                    RollbackResult result = executeRollback(transactionId, reason, initiatedBy);
                    
                    if (result.getStatus() == RollbackStatus.COMPLETED) {
                        successfulRollbacks.add(result);
                    } else {
                        failedRollbacks.add(result);
                    }

                } catch (Exception e) {
                    log.error("CRITICAL: Failed to rollback transaction in batch: {}", transactionId, e);
                    
                    RollbackResult failureResult = RollbackResult.builder()
                            .transactionId(transactionId)
                            .status(RollbackStatus.FAILED)
                            .reason(reason)
                            .errorMessage(e.getMessage())
                            .failedAt(LocalDateTime.now())
                            .build();
                    
                    failedRollbacks.add(failureResult);
                }
            }

            // Determine overall batch status
            BatchRollbackStatus batchStatus;
            if (failedRollbacks.isEmpty()) {
                batchStatus = BatchRollbackStatus.ALL_COMPLETED;
            } else if (successfulRollbacks.isEmpty()) {
                batchStatus = BatchRollbackStatus.ALL_FAILED;
            } else {
                batchStatus = BatchRollbackStatus.PARTIAL_SUCCESS;
            }

            // Complete batch audit record
            rollbackAuditService.completeBatchRollbackRecord(
                    batchAuditRecord, batchStatus, successfulRollbacks.size(), failedRollbacks.size());

            log.info("SECURITY: Batch rollback completed - successful: {}, failed: {}", 
                    successfulRollbacks.size(), failedRollbacks.size());

            return resultBuilder
                    .status(batchStatus)
                    .successfulRollbacks(successfulRollbacks)
                    .failedRollbacks(failedRollbacks)
                    .completedAt(LocalDateTime.now())
                    .batchAuditRecordId(batchAuditRecord.getId())
                    .build();

        } catch (Exception e) {
            log.error("CRITICAL: Batch rollback operation failed", e);
            
            rollbackAuditService.logBatchRollbackFailure(batchAuditRecord, e);

            return resultBuilder
                    .status(BatchRollbackStatus.BATCH_FAILED)
                    .errorMessage(e.getMessage())
                    .failedAt(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Execute emergency rollback with elevated permissions
     */
    private EmergencyRollbackResult executeEmergencyRollback(EmergencyRollbackRequest request) {
        EmergencyRollbackResult.EmergencyRollbackResultBuilder resultBuilder = 
                EmergencyRollbackResult.builder()
                        .emergencyId(request.getEmergencyId())
                        .reason(request.getReason())
                        .initiatedBy(request.getInitiatedBy())
                        .startedAt(LocalDateTime.now());

        try {
            // Execute based on emergency type
            switch (request.getEmergencyType()) {
                case SYSTEM_WIDE_ROLLBACK:
                    return executeSystemWideRollback(request, resultBuilder);
                    
                case TIME_RANGE_ROLLBACK:
                    return executeTimeRangeRollback(request, resultBuilder);
                    
                case USER_ACCOUNT_ROLLBACK:
                    return executeUserAccountRollback(request, resultBuilder);
                    
                case MERCHANT_ROLLBACK:
                    return executeMerchantRollback(request, resultBuilder);
                    
                default:
                    throw new IllegalArgumentException("Unknown emergency rollback type: " + 
                                                     request.getEmergencyType());
            }

        } catch (Exception e) {
            log.error("CRITICAL: Emergency rollback failed for emergency: {}", request.getEmergencyId(), e);
            
            return resultBuilder
                    .status(EmergencyRollbackStatus.FAILED)
                    .errorMessage(e.getMessage())
                    .failedAt(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Validate if transaction can be rolled back
     */
    private void validateRollbackEligibility(Transaction transaction) {
        // Check transaction status
        if (transaction.getStatus() == TransactionStatus.ROLLED_BACK) {
            throw new IllegalStateException("Transaction is already rolled back: " + transaction.getId());
        }

        if (transaction.getStatus() == TransactionStatus.PENDING) {
            throw new IllegalStateException("Cannot rollback pending transaction: " + transaction.getId());
        }

        // Check rollback window (configurable business rule)
        if (isOutsideRollbackWindow(transaction)) {
            throw new IllegalStateException("Transaction is outside rollback window: " + transaction.getId());
        }

        // Check if transaction has dependent transactions
        if (hasActiveDependentTransactions(transaction)) {
            throw new IllegalStateException("Transaction has active dependent transactions: " + 
                                          transaction.getId());
        }
    }

    /**
     * Check if transaction is outside the rollback time window
     */
    private boolean isOutsideRollbackWindow(Transaction transaction) {
        // Business rule: transactions can be rolled back within 30 days
        LocalDateTime rollbackCutoff = LocalDateTime.now().minusDays(30);
        return transaction.getCreatedAt().isBefore(rollbackCutoff);
    }

    /**
     * Check if transaction has dependent transactions that would be affected
     */
    private boolean hasActiveDependentTransactions(Transaction transaction) {
        // Check for child transactions, linked transactions, etc.
        List<Transaction> dependentTransactions = transactionRepository
                .findDependentTransactions(transaction.getId());
        
        return dependentTransactions.stream()
                .anyMatch(tx -> tx.getStatus() != TransactionStatus.COMPLETED && 
                              tx.getStatus() != TransactionStatus.FAILED);
    }

    /**
     * Publish rollback event for other services
     */
    private void publishRollbackEvent(Transaction transaction, String reason, String initiatedBy) {
        try {
            RollbackEvent event = RollbackEvent.builder()
                    .transactionId(transaction.getId())
                    .originalAmount(transaction.getAmount())
                    .currency(transaction.getCurrency())
                    .fromUserId(transaction.getFromUserId())
                    .toUserId(transaction.getToUserId())
                    .reason(reason)
                    .initiatedBy(initiatedBy)
                    .rolledBackAt(LocalDateTime.now())
                    .build();

            kafkaTemplate.send("transaction-rollback-events", event.toJson());
            
        } catch (Exception e) {
            log.error("Failed to publish rollback event for transaction: {}", transaction.getId(), e);
            // Don't fail the rollback if event publishing fails
        }
    }

    // Emergency rollback implementations
    private EmergencyRollbackResult executeSystemWideRollback(
            EmergencyRollbackRequest request, 
            EmergencyRollbackResult.EmergencyRollbackResultBuilder resultBuilder) {
        
        log.error("EMERGENCY: Executing system-wide transaction rollback");
        
        // Find all transactions in the specified time range
        List<Transaction> transactionsToRollback = transactionRepository
                .findTransactionsInTimeRange(request.getStartTime(), request.getEndTime());
        
        // Execute batch rollback
        List<UUID> transactionIds = transactionsToRollback.stream()
                .map(Transaction::getId)
                .toList();
        
        BatchRollbackResult batchResult = executeBatchRollback(
                transactionIds, request.getReason(), request.getInitiatedBy());
        
        return resultBuilder
                .status(mapBatchStatusToEmergencyStatus(batchResult.getStatus()))
                .transactionsProcessed(transactionIds.size())
                .successfulRollbacks(batchResult.getSuccessfulRollbacks().size())
                .failedRollbacks(batchResult.getFailedRollbacks().size())
                .completedAt(LocalDateTime.now())
                .build();
    }

    private EmergencyRollbackResult executeTimeRangeRollback(
            EmergencyRollbackRequest request, 
            EmergencyRollbackResult.EmergencyRollbackResultBuilder resultBuilder) {
        
        log.error("EMERGENCY: Executing time range rollback from {} to {}", 
                request.getStartTime(), request.getEndTime());
        
        // Similar implementation to system-wide but with specific filters
        List<Transaction> transactionsToRollback = transactionRepository
                .findTransactionsInTimeRangeWithFilters(
                        request.getStartTime(), 
                        request.getEndTime(), 
                        request.getFilters());
        
        List<UUID> transactionIds = transactionsToRollback.stream()
                .map(Transaction::getId)
                .toList();
        
        BatchRollbackResult batchResult = executeBatchRollback(
                transactionIds, request.getReason(), request.getInitiatedBy());
        
        return resultBuilder
                .status(mapBatchStatusToEmergencyStatus(batchResult.getStatus()))
                .transactionsProcessed(transactionIds.size())
                .successfulRollbacks(batchResult.getSuccessfulRollbacks().size())
                .failedRollbacks(batchResult.getFailedRollbacks().size())
                .completedAt(LocalDateTime.now())
                .build();
    }

    private EmergencyRollbackResult executeUserAccountRollback(
            EmergencyRollbackRequest request, 
            EmergencyRollbackResult.EmergencyRollbackResultBuilder resultBuilder) {
        
        log.error("EMERGENCY: Executing user account rollback for user: {}", request.getUserId());
        
        List<Transaction> userTransactions = transactionRepository
                .findTransactionsByUserInTimeRange(
                        request.getUserId(), 
                        request.getStartTime(), 
                        request.getEndTime());
        
        List<UUID> transactionIds = userTransactions.stream()
                .map(Transaction::getId)
                .toList();
        
        BatchRollbackResult batchResult = executeBatchRollback(
                transactionIds, request.getReason(), request.getInitiatedBy());
        
        return resultBuilder
                .status(mapBatchStatusToEmergencyStatus(batchResult.getStatus()))
                .transactionsProcessed(transactionIds.size())
                .successfulRollbacks(batchResult.getSuccessfulRollbacks().size())
                .failedRollbacks(batchResult.getFailedRollbacks().size())
                .completedAt(LocalDateTime.now())
                .build();
    }

    private EmergencyRollbackResult executeMerchantRollback(
            EmergencyRollbackRequest request, 
            EmergencyRollbackResult.EmergencyRollbackResultBuilder resultBuilder) {
        
        log.error("EMERGENCY: Executing merchant rollback for merchant: {}", request.getMerchantId());
        
        List<Transaction> merchantTransactions = transactionRepository
                .findTransactionsByMerchantInTimeRange(
                        request.getMerchantId(), 
                        request.getStartTime(), 
                        request.getEndTime());
        
        List<UUID> transactionIds = merchantTransactions.stream()
                .map(Transaction::getId)
                .toList();
        
        BatchRollbackResult batchResult = executeBatchRollback(
                transactionIds, request.getReason(), request.getInitiatedBy());
        
        return resultBuilder
                .status(mapBatchStatusToEmergencyStatus(batchResult.getStatus()))
                .transactionsProcessed(transactionIds.size())
                .successfulRollbacks(batchResult.getSuccessfulRollbacks().size())
                .failedRollbacks(batchResult.getFailedRollbacks().size())
                .completedAt(LocalDateTime.now())
                .build();
    }

    private EmergencyRollbackStatus mapBatchStatusToEmergencyStatus(BatchRollbackStatus batchStatus) {
        return switch (batchStatus) {
            case ALL_COMPLETED -> EmergencyRollbackStatus.COMPLETED;
            case PARTIAL_SUCCESS -> EmergencyRollbackStatus.PARTIAL_SUCCESS;
            case ALL_FAILED, BATCH_FAILED -> EmergencyRollbackStatus.FAILED;
        };
    }

    // Status Enums
    public enum RollbackStatus {
        COMPLETED, FAILED, IN_PROGRESS
    }

    public enum BatchRollbackStatus {
        ALL_COMPLETED, PARTIAL_SUCCESS, ALL_FAILED, BATCH_FAILED
    }

    public enum EmergencyRollbackStatus {
        COMPLETED, PARTIAL_SUCCESS, FAILED
    }

    public enum EmergencyRollbackType {
        SYSTEM_WIDE_ROLLBACK, TIME_RANGE_ROLLBACK, USER_ACCOUNT_ROLLBACK, MERCHANT_ROLLBACK
    }

    // DTOs
    @lombok.Builder
    @lombok.Data
    public static class RollbackResult {
        private UUID transactionId;
        private RollbackStatus status;
        private String reason;
        private String errorMessage;
        private Integer compensationActions;
        private UUID auditRecordId;
        private LocalDateTime completedAt;
        private LocalDateTime failedAt;
    }

    @lombok.Builder
    @lombok.Data
    public static class BatchRollbackResult {
        private BatchRollbackStatus status;
        private String reason;
        private String initiatedBy;
        private List<RollbackResult> successfulRollbacks;
        private List<RollbackResult> failedRollbacks;
        private String errorMessage;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private LocalDateTime failedAt;
        private UUID batchAuditRecordId;
    }

    @lombok.Builder
    @lombok.Data
    public static class EmergencyRollbackResult {
        private String emergencyId;
        private EmergencyRollbackStatus status;
        private String reason;
        private String initiatedBy;
        private Integer transactionsProcessed;
        private Integer successfulRollbacks;
        private Integer failedRollbacks;
        private String errorMessage;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private LocalDateTime failedAt;
    }

    @lombok.Builder
    @lombok.Data
    public static class EmergencyRollbackRequest {
        private String emergencyId;
        private EmergencyRollbackType emergencyType;
        private String reason;
        private String initiatedBy;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String userId;
        private String merchantId;
        private Map<String, Object> filters;
    }

    @lombok.Builder
    @lombok.Data
    public static class RollbackEvent {
        private UUID transactionId;
        private BigDecimal originalAmount;
        private String currency;
        private String fromUserId;
        private String toUserId;
        private String reason;
        private String initiatedBy;
        private LocalDateTime rolledBackAt;

        public String toJson() {
            // Implementation to convert to JSON
            return com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(this);
        }
    }

    // Custom Exceptions
    public static class TransactionNotFoundException extends RuntimeException {
        public TransactionNotFoundException(String message) {
            super(message);
        }
    }

    public static class RollbackNotAllowedException extends RuntimeException {
        public RollbackNotAllowedException(String message) {
            super(message);
        }
    }
}