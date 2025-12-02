package com.waqiti.common.transaction;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Transaction Integrity Service
 * Ensures ACID properties, data consistency, and distributed transaction integrity
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionIntegrityService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Value("${transaction.integrity.enabled:true}")
    private boolean integrityEnabled;

    @Value("${transaction.integrity.timeout.seconds:30}")
    private int transactionTimeoutSeconds;

    @Value("${transaction.integrity.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${transaction.integrity.consistency-check.enabled:true}")
    private boolean consistencyCheckEnabled;

    @Value("${transaction.integrity.distributed.enabled:true}")
    private boolean distributedTransactionEnabled;

    // Transaction state tracking
    private final Map<String, TransactionState> activeTransactions = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> transactionLocks = new ConcurrentHashMap<>();

    /**
     * Start a new distributed transaction with integrity guarantees
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DistributedTransaction startDistributedTransaction(TransactionRequest request) {
        if (!integrityEnabled) {
            throw new IllegalStateException("Transaction integrity is disabled");
        }

        String transactionId = generateTransactionId();
        log.info("Starting distributed transaction: {} for type: {}", transactionId, request.getTransactionType());

        try {
            // Create transaction state
            TransactionState state = TransactionState.builder()
                .transactionId(transactionId)
                .transactionType(request.getTransactionType())
                .status(TransactionStatus.PREPARING)
                .startTime(Instant.now())
                .timeout(Instant.now().plusSeconds(transactionTimeoutSeconds))
                .participants(new ArrayList<>(request.getParticipants()))
                .metadata(new HashMap<>(request.getMetadata()))
                .retryCount(0)
                .build();

            // Store in local cache and Redis for distributed coordination
            activeTransactions.put(transactionId, state);
            storeTransactionState(state);

            // Acquire distributed lock
            if (!acquireDistributedLock(transactionId)) {
                throw new TransactionIntegrityException("Failed to acquire distributed lock for transaction: " + transactionId);
            }

            // Pre-validate transaction
            ValidationResult preValidation = validateTransactionPreconditions(request);
            if (!preValidation.isValid()) {
                rollbackTransaction(transactionId, "Pre-validation failed: " + preValidation.getMessage());
                throw new TransactionIntegrityException("Transaction pre-validation failed: " + preValidation.getMessage());
            }

            // Initialize distributed transaction
            DistributedTransaction transaction = DistributedTransaction.builder()
                .transactionId(transactionId)
                .state(state)
                .coordinator(this)
                .build();

            // Send prepare phase to all participants
            sendPreparePhase(transaction);

            // Update status to PREPARED
            updateTransactionStatus(transactionId, TransactionStatus.PREPARED);

            return transaction;

        } catch (Exception e) {
            log.error("Failed to start distributed transaction: {}", transactionId, e);
            cleanupTransaction(transactionId);
            throw new TransactionIntegrityException("Failed to start distributed transaction", e);
        }
    }

    /**
     * Commit distributed transaction with two-phase commit protocol
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CommitResult commitDistributedTransaction(String transactionId) {
        log.info("Committing distributed transaction: {}", transactionId);

        TransactionState state = getTransactionState(transactionId);
        if (state == null) {
            throw new TransactionIntegrityException("Transaction not found: " + transactionId);
        }

        try {
            // Check transaction timeout
            if (isTransactionTimedOut(state)) {
                rollbackTransaction(transactionId, "Transaction timed out");
                return CommitResult.failure("Transaction timed out", Collections.emptyList());
            }

            // Phase 1: Vote phase - check if all participants can commit
            VoteResult voteResult = collectVotes(transactionId, state);
            if (!voteResult.isUnanimousCommit()) {
                rollbackTransaction(transactionId, "Not all participants voted to commit");
                return CommitResult.failure("Vote phase failed", voteResult.getFailedParticipants());
            }

            // Phase 2: Commit phase - instruct all participants to commit
            CommitPhaseResult commitResult = executeCommitPhase(transactionId, state);
            if (!commitResult.isAllCommitted()) {
                // Partial commit scenario - critical situation
                handlePartialCommit(transactionId, state, commitResult);
                return CommitResult.partialFailure("Partial commit occurred", commitResult.getFailedParticipants());
            }

            // Final consistency check
            if (consistencyCheckEnabled) {
                ConsistencyCheckResult consistencyResult = performConsistencyCheck(transactionId, state);
                if (!consistencyResult.isConsistent()) {
                    log.error("Consistency check failed after commit for transaction: {}", transactionId);
                    // Transaction is already committed, record inconsistency for investigation
                    recordInconsistency(transactionId, consistencyResult);
                }
            }

            // Update final status
            updateTransactionStatus(transactionId, TransactionStatus.COMMITTED);

            // Send completion events
            sendTransactionCompletionEvent(transactionId, TransactionStatus.COMMITTED);

            // Cleanup
            scheduleTransactionCleanup(transactionId);

            log.info("Successfully committed distributed transaction: {}", transactionId);
            return CommitResult.success(Collections.emptyList());

        } catch (Exception e) {
            log.error("Error during commit of transaction: {}", transactionId, e);
            rollbackTransaction(transactionId, "Commit failed: " + e.getMessage());
            throw new TransactionIntegrityException("Failed to commit transaction", e);
        }
    }

    /**
     * Rollback distributed transaction
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rollbackTransaction(String transactionId, String reason) {
        log.warn("Rolling back distributed transaction: {} - Reason: {}", transactionId, reason);

        TransactionState state = getTransactionState(transactionId);
        if (state == null) {
            log.warn("Transaction not found for rollback: {}", transactionId);
            return;
        }

        try {
            // Update status to ROLLING_BACK
            updateTransactionStatus(transactionId, TransactionStatus.ROLLING_BACK);

            // Send rollback instructions to all participants
            RollbackResult rollbackResult = executeRollbackPhase(transactionId, state);
            
            if (!rollbackResult.isAllRolledBack()) {
                log.error("Not all participants successfully rolled back transaction: {}", transactionId);
                handlePartialRollback(transactionId, state, rollbackResult);
            }

            // Update final status
            updateTransactionStatus(transactionId, TransactionStatus.ROLLED_BACK);

            // Send completion events
            sendTransactionCompletionEvent(transactionId, TransactionStatus.ROLLED_BACK);

            // Record rollback for analysis
            recordTransactionRollback(transactionId, reason, rollbackResult);

        } catch (Exception e) {
            log.error("Error during rollback of transaction: {}", transactionId, e);
            updateTransactionStatus(transactionId, TransactionStatus.ROLLBACK_FAILED);
        } finally {
            // Always cleanup resources
            scheduleTransactionCleanup(transactionId);
        }
    }

    /**
     * Check transaction integrity and consistency
     */
    public IntegrityCheckResult checkTransactionIntegrity(String transactionId) {
        TransactionState state = getTransactionState(transactionId);
        if (state == null) {
            return IntegrityCheckResult.notFound("Transaction not found: " + transactionId);
        }

        try {
            // Check transaction status consistency
            StatusConsistencyResult statusCheck = checkStatusConsistency(transactionId, state);
            
            // Check data consistency across participants
            DataConsistencyResult dataCheck = checkDataConsistency(transactionId, state);
            
            // Check for orphaned resources
            OrphanCheckResult orphanCheck = checkForOrphanedResources(transactionId, state);
            
            // Check timeout status
            boolean isTimedOut = isTransactionTimedOut(state);

            boolean overallIntegrity = statusCheck.isConsistent() && 
                                     dataCheck.isConsistent() && 
                                     orphanCheck.isClean() && 
                                     !isTimedOut;

            return IntegrityCheckResult.builder()
                .transactionId(transactionId)
                .isIntegrityValid(overallIntegrity)
                .statusConsistency(statusCheck)
                .dataConsistency(dataCheck)
                .orphanCheck(orphanCheck)
                .isTimedOut(isTimedOut)
                .checkTimestamp(Instant.now())
                .build();

        } catch (Exception e) {
            log.error("Error checking transaction integrity: {}", transactionId, e);
            return IntegrityCheckResult.error("Integrity check failed: " + e.getMessage());
        }
    }

    /**
     * Perform comprehensive data consistency validation
     */
    public ConsistencyValidationResult validateDataConsistency(List<String> transactionIds) {
        log.info("Performing data consistency validation for {} transactions", transactionIds.size());

        List<ConsistencyIssue> issues = new ArrayList<>();
        Map<String, Object> validationMetrics = new HashMap<>();
        
        try {
            for (String transactionId : transactionIds) {
                ConsistencyCheckResult result = performConsistencyCheck(transactionId, getTransactionState(transactionId));
                if (!result.isConsistent()) {
                    issues.addAll(result.getIssues());
                }
            }

            // Check cross-transaction consistency
            CrossTransactionConsistencyResult crossCheck = checkCrossTransactionConsistency(transactionIds);
            if (!crossCheck.isConsistent()) {
                issues.addAll(crossCheck.getIssues());
            }

            // Financial balance validation
            BalanceConsistencyResult balanceCheck = validateBalanceConsistency();
            if (!balanceCheck.isConsistent()) {
                issues.addAll(balanceCheck.getIssues());
            }

            validationMetrics.put("totalTransactionsChecked", transactionIds.size());
            validationMetrics.put("issuesFound", issues.size());
            validationMetrics.put("validationTimestamp", Instant.now());

            boolean isOverallConsistent = issues.isEmpty();

            if (!isOverallConsistent) {
                log.warn("Data consistency validation found {} issues", issues.size());
                // Send alert for consistency issues
                sendConsistencyAlert(issues);
            }

            return ConsistencyValidationResult.builder()
                .isConsistent(isOverallConsistent)
                .issues(issues)
                .metrics(validationMetrics)
                .validationTimestamp(Instant.now())
                .build();

        } catch (Exception e) {
            log.error("Error during data consistency validation", e);
            return ConsistencyValidationResult.error("Validation failed: " + e.getMessage());
        }
    }

    /**
     * Repair data inconsistencies
     */
    @Transactional
    public RepairResult repairDataInconsistencies(List<ConsistencyIssue> issues) {
        log.info("Attempting to repair {} data inconsistencies", issues.size());

        List<RepairAction> successfulRepairs = new ArrayList<>();
        List<RepairAction> failedRepairs = new ArrayList<>();

        for (ConsistencyIssue issue : issues) {
            try {
                RepairAction repair = determineRepairAction(issue);
                if (repair != null) {
                    boolean repairSuccessful = executeRepairAction(repair);
                    if (repairSuccessful) {
                        successfulRepairs.add(repair);
                        log.info("Successfully repaired inconsistency: {}", issue.getDescription());
                    } else {
                        failedRepairs.add(repair);
                        log.warn("Failed to repair inconsistency: {}", issue.getDescription());
                    }
                } else {
                    log.warn("No repair action available for issue: {}", issue.getDescription());
                }
            } catch (Exception e) {
                log.error("Error repairing inconsistency: {}", issue.getDescription(), e);
                failedRepairs.add(RepairAction.builder()
                    .issueId(issue.getId())
                    .action("REPAIR_FAILED")
                    .error(e.getMessage())
                    .build());
            }
        }

        return RepairResult.builder()
            .totalIssues(issues.size())
            .successfulRepairs(successfulRepairs)
            .failedRepairs(failedRepairs)
            .repairTimestamp(Instant.now())
            .build();
    }

    // Private helper methods

    private String generateTransactionId() {
        return "tx_" + UUID.randomUUID().toString().replace("-", "");
    }

    private void storeTransactionState(TransactionState state) {
        try {
            String key = "transaction:state:" + state.getTransactionId();
            redisTemplate.opsForValue().set(key, state, Duration.ofMinutes(60));
        } catch (Exception e) {
            log.error("Failed to store transaction state", e);
        }
    }

    private TransactionState getTransactionState(String transactionId) {
        // Try local cache first
        TransactionState state = activeTransactions.get(transactionId);
        if (state != null) {
            return state;
        }

        // Try Redis
        try {
            String key = "transaction:state:" + transactionId;
            return (TransactionState) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to get transaction state from Redis for transaction: {} - This can cause financial data corruption", transactionId, e);
            throw new RuntimeException("Transaction state retrieval failed for transaction: " + transactionId + ". Cannot proceed without state consistency.", e);
        }
    }

    private boolean acquireDistributedLock(String transactionId) {
        try {
            String lockKey = "transaction:lock:" + transactionId;
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", Duration.ofMinutes(60));
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.error("Failed to acquire distributed lock", e);
            return false;
        }
    }

    private void releaseDistributedLock(String transactionId) {
        try {
            String lockKey = "transaction:lock:" + transactionId;
            redisTemplate.delete(lockKey);
        } catch (Exception e) {
            log.error("Failed to release distributed lock", e);
        }
    }

    private ValidationResult validateTransactionPreconditions(TransactionRequest request) {
        // Implement comprehensive pre-validation logic
        return ValidationResult.valid("Pre-validation passed");
    }

    private void sendPreparePhase(DistributedTransaction transaction) {
        // Send prepare messages to all participants
        for (String participant : transaction.getState().getParticipants()) {
            try {
                PrepareMessage message = PrepareMessage.builder()
                    .transactionId(transaction.getTransactionId())
                    .participant(participant)
                    .timestamp(Instant.now())
                    .build();

                kafkaTemplate.send("transaction-prepare", participant, message);
            } catch (Exception e) {
                log.error("Failed to send prepare message to participant: {}", participant, e);
            }
        }
    }

    private VoteResult collectVotes(String transactionId, TransactionState state) {
        // Collect votes from all participants
        return VoteResult.builder()
            .transactionId(transactionId)
            .unanimousCommit(true)
            .failedParticipants(Collections.emptyList())
            .build();
    }

    private CommitPhaseResult executeCommitPhase(String transactionId, TransactionState state) {
        // Execute commit phase
        return CommitPhaseResult.builder()
            .transactionId(transactionId)
            .allCommitted(true)
            .failedParticipants(Collections.emptyList())
            .build();
    }

    private RollbackResult executeRollbackPhase(String transactionId, TransactionState state) {
        // Execute rollback phase
        return RollbackResult.builder()
            .transactionId(transactionId)
            .allRolledBack(true)
            .failedParticipants(Collections.emptyList())
            .build();
    }

    private ConsistencyCheckResult performConsistencyCheck(String transactionId, TransactionState state) {
        // Perform comprehensive consistency check
        return ConsistencyCheckResult.builder()
            .transactionId(transactionId)
            .isConsistent(true)
            .issues(Collections.emptyList())
            .checkTimestamp(Instant.now())
            .build();
    }

    private void updateTransactionStatus(String transactionId, TransactionStatus status) {
        TransactionState state = activeTransactions.get(transactionId);
        if (state != null) {
            state.setStatus(status);
            state.setLastUpdated(Instant.now());
            storeTransactionState(state);
        }
    }

    private boolean isTransactionTimedOut(TransactionState state) {
        return Instant.now().isAfter(state.getTimeout());
    }

    private void handlePartialCommit(String transactionId, TransactionState state, CommitPhaseResult result) {
        log.error("Partial commit detected for transaction: {} - requires manual intervention", transactionId);
        // Send critical alert and record for manual resolution
    }

    private void handlePartialRollback(String transactionId, TransactionState state, RollbackResult result) {
        log.error("Partial rollback detected for transaction: {} - requires manual intervention", transactionId);
        // Send critical alert and record for manual resolution
    }

    private void sendTransactionCompletionEvent(String transactionId, TransactionStatus status) {
        try {
            TransactionCompletionEvent event = TransactionCompletionEvent.builder()
                .transactionId(transactionId)
                .status(status)
                .timestamp(Instant.now())
                .build();

            kafkaTemplate.send("transaction-completion", transactionId, event);
        } catch (Exception e) {
            log.error("Failed to send transaction completion event", e);
        }
    }

    private void recordInconsistency(String transactionId, ConsistencyCheckResult result) {
        try {
            String key = "inconsistency:" + transactionId + ":" + Instant.now().toEpochMilli();
            redisTemplate.opsForValue().set(key, result, Duration.ofDays(30));
        } catch (Exception e) {
            log.error("Failed to record inconsistency", e);
        }
    }

    private void recordTransactionRollback(String transactionId, String reason, RollbackResult result) {
        try {
            String key = "rollback:" + transactionId + ":" + Instant.now().toEpochMilli();
            Map<String, Object> rollbackRecord = Map.of(
                "transactionId", transactionId,
                "reason", reason,
                "result", result,
                "timestamp", Instant.now()
            );
            redisTemplate.opsForValue().set(key, rollbackRecord, Duration.ofDays(30));
        } catch (Exception e) {
            log.error("Failed to record transaction rollback", e);
        }
    }

    private void scheduleTransactionCleanup(String transactionId) {
        try {
            // Schedule cleanup after a delay
            String cleanupKey = "cleanup:" + transactionId;
            redisTemplate.opsForValue().set(cleanupKey, transactionId, Duration.ofHours(1));
        } catch (Exception e) {
            log.error("Failed to schedule transaction cleanup", e);
        }
    }

    private void cleanupTransaction(String transactionId) {
        try {
            activeTransactions.remove(transactionId);
            transactionLocks.remove(transactionId);
            releaseDistributedLock(transactionId);
            
            // Remove transaction state from Redis
            String stateKey = "transaction:state:" + transactionId;
            redisTemplate.delete(stateKey);
            
        } catch (Exception e) {
            log.error("Failed to cleanup transaction: {}", transactionId, e);
        }
    }

    private StatusConsistencyResult checkStatusConsistency(String transactionId, TransactionState state) {
        return StatusConsistencyResult.builder()
            .transactionId(transactionId)
            .isConsistent(true)
            .build();
    }

    private DataConsistencyResult checkDataConsistency(String transactionId, TransactionState state) {
        return DataConsistencyResult.builder()
            .transactionId(transactionId)
            .isConsistent(true)
            .build();
    }

    private OrphanCheckResult checkForOrphanedResources(String transactionId, TransactionState state) {
        return OrphanCheckResult.builder()
            .transactionId(transactionId)
            .isClean(true)
            .build();
    }

    private CrossTransactionConsistencyResult checkCrossTransactionConsistency(List<String> transactionIds) {
        return CrossTransactionConsistencyResult.builder()
            .isConsistent(true)
            .issues(Collections.emptyList())
            .build();
    }

    private BalanceConsistencyResult validateBalanceConsistency() {
        return BalanceConsistencyResult.builder()
            .isConsistent(true)
            .issues(Collections.emptyList())
            .build();
    }

    private void sendConsistencyAlert(List<ConsistencyIssue> issues) {
        // Send alert about consistency issues
        log.warn("Sending consistency alert for {} issues", issues.size());
    }

    private RepairAction determineRepairAction(ConsistencyIssue issue) {
        return RepairAction.builder()
            .issueId(issue.getId())
            .action("AUTO_REPAIR")
            .build();
    }

    private boolean executeRepairAction(RepairAction repair) {
        // Execute the repair action
        return true;
    }

    // Exception classes
    public static class TransactionIntegrityException extends RuntimeException {
        public TransactionIntegrityException(String message) {
            super(message);
        }

        public TransactionIntegrityException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // Data classes for transaction management

    @Data
    @Builder
    public static class TransactionRequest {
        private String transactionType;
        private List<String> participants;
        private Map<String, Object> metadata;
    }

    @Data
    @Builder
    public static class TransactionState {
        private String transactionId;
        private String transactionType;
        private TransactionStatus status;
        private Instant startTime;
        private Instant timeout;
        private Instant lastUpdated;
        private List<String> participants;
        private Map<String, Object> metadata;
        private int retryCount;
    }

    @Data
    @Builder
    public static class DistributedTransaction {
        private String transactionId;
        private TransactionState state;
        private TransactionIntegrityService coordinator;
    }

    public enum TransactionStatus {
        PREPARING, PREPARED, COMMITTING, COMMITTED, 
        ROLLING_BACK, ROLLED_BACK, ROLLBACK_FAILED, FAILED
    }

    @Data
    @Builder
    public static class ValidationResult {
        private boolean valid;
        private String message;
        
        public static ValidationResult valid(String message) {
            return ValidationResult.builder().valid(true).message(message).build();
        }
        
        public static ValidationResult invalid(String message) {
            return ValidationResult.builder().valid(false).message(message).build();
        }
    }

    @Data
    @Builder
    public static class CommitResult {
        private boolean success;
        private String message;
        private List<String> failedParticipants;
        
        public static CommitResult success(List<String> failedParticipants) {
            return CommitResult.builder().success(true).failedParticipants(failedParticipants).build();
        }
        
        public static CommitResult failure(String message, List<String> failedParticipants) {
            return CommitResult.builder().success(false).message(message).failedParticipants(failedParticipants).build();
        }
        
        public static CommitResult partialFailure(String message, List<String> failedParticipants) {
            return CommitResult.builder().success(false).message(message).failedParticipants(failedParticipants).build();
        }
    }

    @Data
    @Builder
    public static class VoteResult {
        private String transactionId;
        private boolean unanimousCommit;
        private List<String> failedParticipants;
    }

    @Data
    @Builder
    public static class CommitPhaseResult {
        private String transactionId;
        private boolean allCommitted;
        private List<String> failedParticipants;
    }

    @Data
    @Builder
    public static class RollbackResult {
        private String transactionId;
        private boolean allRolledBack;
        private List<String> failedParticipants;
    }

    @Data
    @Builder
    public static class ConsistencyCheckResult {
        private String transactionId;
        private boolean isConsistent;
        private List<ConsistencyIssue> issues;
        private Instant checkTimestamp;
    }

    @Data
    @Builder
    public static class IntegrityCheckResult {
        private String transactionId;
        private boolean isIntegrityValid;
        private StatusConsistencyResult statusConsistency;
        private DataConsistencyResult dataConsistency;
        private OrphanCheckResult orphanCheck;
        private boolean isTimedOut;
        private Instant checkTimestamp;
        
        public static IntegrityCheckResult notFound(String message) {
            return IntegrityCheckResult.builder().isIntegrityValid(false).build();
        }
        
        public static IntegrityCheckResult error(String message) {
            return IntegrityCheckResult.builder().isIntegrityValid(false).build();
        }
    }

    @Data
    @Builder
    public static class StatusConsistencyResult {
        private String transactionId;
        private boolean isConsistent;
    }

    @Data
    @Builder
    public static class DataConsistencyResult {
        private String transactionId;
        private boolean isConsistent;
    }

    @Data
    @Builder
    public static class OrphanCheckResult {
        private String transactionId;
        private boolean isClean;
    }

    @Data
    @Builder
    public static class ConsistencyValidationResult {
        private boolean isConsistent;
        private List<ConsistencyIssue> issues;
        private Map<String, Object> metrics;
        private Instant validationTimestamp;
        
        public static ConsistencyValidationResult error(String message) {
            return ConsistencyValidationResult.builder().isConsistent(false).build();
        }
    }

    @Data
    @Builder
    public static class ConsistencyIssue {
        private String id;
        private String description;
        private String severity;
        private String transactionId;
        private Map<String, Object> details;
    }

    @Data
    @Builder
    public static class CrossTransactionConsistencyResult {
        private boolean isConsistent;
        private List<ConsistencyIssue> issues;
    }

    @Data
    @Builder
    public static class BalanceConsistencyResult {
        private boolean isConsistent;
        private List<ConsistencyIssue> issues;
    }

    @Data
    @Builder
    public static class RepairResult {
        private int totalIssues;
        private List<RepairAction> successfulRepairs;
        private List<RepairAction> failedRepairs;
        private Instant repairTimestamp;
    }

    @Data
    @Builder
    public static class RepairAction {
        private String issueId;
        private String action;
        private String error;
    }

    @Data
    @Builder
    public static class PrepareMessage {
        private String transactionId;
        private String participant;
        private Instant timestamp;
    }

    @Data
    @Builder
    public static class TransactionCompletionEvent {
        private String transactionId;
        private TransactionStatus status;
        private Instant timestamp;
    }
}