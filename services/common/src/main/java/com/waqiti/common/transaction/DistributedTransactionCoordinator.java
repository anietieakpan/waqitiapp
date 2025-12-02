package com.waqiti.common.transaction;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Distributed Transaction Coordinator
 * Implements SAGA pattern and two-phase commit for distributed transactions
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedTransactionCoordinator {

    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TransactionIntegrityService integrityService;

    @Value("${transaction.coordinator.timeout.minutes:30}")
    private int transactionTimeoutMinutes;

    @Value("${transaction.coordinator.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${transaction.coordinator.saga.enabled:true}")
    private boolean sagaEnabled;

    @Value("${transaction.coordinator.two-phase.enabled:true}")
    private boolean twoPhaseCommitEnabled;

    @Value("${transaction.coordinator.recovery.enabled:true}")
    private boolean recoveryEnabled;

    // Active transaction state tracking
    private final Map<String, DistributedTransactionState> activeTransactions = new ConcurrentHashMap<>();
    
    // Executor for async operations
    private final ExecutorService coordinatorExecutor = Executors.newFixedThreadPool(10);

    /**
     * Start a new SAGA distributed transaction
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public com.waqiti.common.transaction.model.SagaTransaction startSagaTransaction(SagaTransactionRequest request) {
        if (!sagaEnabled) {
            throw new IllegalStateException("SAGA transactions are disabled");
        }

        String transactionId = generateTransactionId();
        log.info("Starting SAGA transaction: {} with {} steps", transactionId, request.getSteps().size());

        try {
            // Create transaction state
            DistributedTransactionState state = DistributedTransactionState.builder()
                .transactionId(transactionId)
                .type(TransactionType.SAGA)
                .status(TransactionStatus.STARTED)
                .startTime(Instant.now())
                .timeout(Instant.now().plusSeconds(transactionTimeoutMinutes * 60L))
                .steps(new ArrayList<>(request.getSteps()))
                .metadata(new HashMap<>(request.getMetadata()))
                .currentStep(0)
                .compensationSteps(new ArrayList<>())
                .retryCount(0)
                .build();

            // Store transaction state
            activeTransactions.put(transactionId, state);
            storeTransactionState(state);

            // Create SAGA transaction model
            com.waqiti.common.transaction.model.SagaTransaction saga =
                com.waqiti.common.transaction.model.SagaTransaction.builder()
                    .transactionId(transactionId)
                    .name(request.getDescription() != null ? request.getDescription() : "SAGA-" + transactionId)
                    .description(request.getDescription())
                    .steps(convertToSagaSteps(request.getSteps()))
                    .currentStepIndex(0)
                    .state(com.waqiti.common.transaction.model.SagaTransaction.SagaState.STARTING)
                    .metadata(new HashMap<>(request.getMetadata()))
                    .build();

            // Start executing steps asynchronously
            executeNextSagaStep(createSagaFromState(state));

            return saga;

        } catch (Exception e) {
            log.error("Failed to start SAGA transaction: {}", transactionId, e);
            cleanupTransactionState(transactionId);
            throw new TransactionCoordinatorException("Failed to start SAGA transaction", e);
        }
    }

    /**
     * Start a two-phase commit transaction
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public com.waqiti.common.transaction.model.TwoPhaseCommitTransaction startTwoPhaseCommit(TwoPhaseCommitRequest request) {
        if (!twoPhaseCommitEnabled) {
            throw new IllegalStateException("Two-phase commit is disabled");
        }

        String transactionId = generateTransactionId();
        log.info("Starting 2PC transaction: {} with {} participants", 
                transactionId, request.getParticipants().size());

        try {
            // Create transaction state
            DistributedTransactionState state = DistributedTransactionState.builder()
                .transactionId(transactionId)
                .type(TransactionType.TWO_PHASE_COMMIT)
                .status(TransactionStatus.STARTED)
                .startTime(Instant.now())
                .timeout(Instant.now().plusSeconds(transactionTimeoutMinutes * 60L))
                .participants(new ArrayList<>(request.getParticipants()))
                .metadata(new HashMap<>(request.getMetadata()))
                .votes(new ConcurrentHashMap<>())
                .retryCount(0)
                .build();

            // Store transaction state
            activeTransactions.put(transactionId, state);
            storeTransactionState(state);

            // Create 2PC transaction model
            com.waqiti.common.transaction.model.TwoPhaseCommitTransaction twoPC =
                com.waqiti.common.transaction.model.TwoPhaseCommitTransaction.builder()
                    .transactionId(transactionId)
                    .name(request.getDescription() != null ? request.getDescription() : "2PC-" + transactionId)
                    .globalTransactionId(transactionId)
                    .phase(com.waqiti.common.transaction.model.TwoPhaseCommitTransaction.TransactionPhase.PREPARING)
                    .state(com.waqiti.common.transaction.model.TwoPhaseCommitTransaction.TransactionState.ACTIVE)
                    .metadata(new HashMap<>(request.getMetadata()))
                    .votes(new ConcurrentHashMap<>())
                    .build();

            // Start prepare phase
            startPreparePhase(createTwoPhaseCommitFromState(state));

            return twoPC;

        } catch (Exception e) {
            log.error("Failed to start 2PC transaction: {}", transactionId, e);
            cleanupTransactionState(transactionId);
            throw new TransactionCoordinatorException("Failed to start 2PC transaction", e);
        }
    }

    /**
     * Get transaction state by ID
     */
    public DistributedTransactionState getTransactionState(String transactionId) {
        return activeTransactions.get(transactionId);
    }

    /**
     * Execute next step in SAGA transaction
     */
    @Async
    public void executeNextSagaStep(SagaTransaction saga) {
        DistributedTransactionState state = activeTransactions.get(saga.getTransactionId());
        if (state == null) {
            log.error("Transaction state not found for SAGA: {}", saga.getTransactionId());
            return;
        }

        try {
            if (state.getCurrentStep() >= state.getSteps().size()) {
                // All steps completed successfully
                completeSagaTransaction(saga);
                return;
            }

            TransactionStep currentStep = state.getSteps().get(state.getCurrentStep());
            log.info("Executing SAGA step {}/{} for transaction: {}", 
                    state.getCurrentStep() + 1, state.getSteps().size(), saga.getTransactionId());

            // Update status
            updateTransactionStatus(saga.getTransactionId(), TransactionStatus.EXECUTING_STEP);

            // Execute step
            StepExecutionResult result = executeTransactionStep(currentStep, saga.getTransactionId());

            if (result.isSuccess()) {
                // Step succeeded, move to next
                state.setCurrentStep(state.getCurrentStep() + 1);
                state.getCompensationSteps().add(currentStep.getCompensationStep());
                updateTransactionState(state);

                // Execute next step
                executeNextSagaStep(saga);

            } else {
                // Step failed, start compensation
                log.warn("SAGA step failed for transaction: {} - {}", 
                        saga.getTransactionId(), result.getErrorMessage());
                startSagaCompensation(saga, result.getErrorMessage());
            }

        } catch (Exception e) {
            log.error("Error executing SAGA step for transaction: {}", saga.getTransactionId(), e);
            startSagaCompensation(saga, "Step execution error: " + e.getMessage());
        }
    }

    /**
     * Start compensation for failed SAGA transaction
     */
    private void startSagaCompensation(SagaTransaction saga, String reason) {
        DistributedTransactionState state = activeTransactions.get(saga.getTransactionId());
        if (state == null) {
            log.error("Transaction state not found for SAGA compensation: {}", saga.getTransactionId());
            return;
        }

        log.info("Starting SAGA compensation for transaction: {} - Reason: {}", 
                saga.getTransactionId(), reason);

        try {
            updateTransactionStatus(saga.getTransactionId(), TransactionStatus.COMPENSATING);

            // Execute compensation steps in reverse order
            List<TransactionStep> compensationSteps = new ArrayList<>(state.getCompensationSteps());
            Collections.reverse(compensationSteps);

            CompletableFuture<Void> compensationFuture = CompletableFuture.runAsync(() -> {
                for (TransactionStep compensationStep : compensationSteps) {
                    try {
                        log.debug("Executing compensation step for transaction: {}", saga.getTransactionId());
                        StepExecutionResult result = executeTransactionStep(compensationStep, saga.getTransactionId());
                        
                        if (!result.isSuccess()) {
                            log.error("Compensation step failed for transaction: {} - {}", 
                                    saga.getTransactionId(), result.getErrorMessage());
                            // Continue with other compensation steps
                        }
                    } catch (Exception e) {
                        log.error("Error executing compensation step for transaction: {}", 
                                saga.getTransactionId(), e);
                    }
                }
            }, coordinatorExecutor);

            compensationFuture.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("SAGA compensation failed for transaction: {}", saga.getTransactionId(), throwable);
                    updateTransactionStatus(saga.getTransactionId(), TransactionStatus.COMPENSATION_FAILED);
                } else {
                    log.info("SAGA compensation completed for transaction: {}", saga.getTransactionId());
                    updateTransactionStatus(saga.getTransactionId(), TransactionStatus.COMPENSATED);
                }
                
                // Clean up transaction state
                scheduleTransactionCleanup(saga.getTransactionId());
            });

        } catch (Exception e) {
            log.error("Failed to start SAGA compensation for transaction: {}", saga.getTransactionId(), e);
            updateTransactionStatus(saga.getTransactionId(), TransactionStatus.COMPENSATION_FAILED);
            scheduleTransactionCleanup(saga.getTransactionId());
        }
    }

    /**
     * Complete successful SAGA transaction
     */
    private void completeSagaTransaction(SagaTransaction saga) {
        log.info("SAGA transaction completed successfully: {}", saga.getTransactionId());
        
        updateTransactionStatus(saga.getTransactionId(), TransactionStatus.COMPLETED);
        
        // Send completion event
        sendTransactionCompletionEvent(saga.getTransactionId(), TransactionStatus.COMPLETED);
        
        // Schedule cleanup
        scheduleTransactionCleanup(saga.getTransactionId());
    }

    /**
     * Start prepare phase for two-phase commit
     */
    private void startPreparePhase(TwoPhaseCommitTransaction twoPC) {
        DistributedTransactionState state = activeTransactions.get(twoPC.getTransactionId());
        if (state == null) {
            log.error("Transaction state not found for 2PC prepare: {}", twoPC.getTransactionId());
            return;
        }

        log.info("Starting prepare phase for 2PC transaction: {}", twoPC.getTransactionId());

        try {
            updateTransactionStatus(twoPC.getTransactionId(), TransactionStatus.PREPARING);

            // Send prepare messages to all participants
            for (TransactionParticipant participant : state.getParticipants()) {
                sendPrepareMessage(twoPC.getTransactionId(), participant);
            }

            // Set timeout for collecting votes
            scheduleVoteCollection(twoPC);

        } catch (Exception e) {
            log.error("Failed to start prepare phase for transaction: {}", twoPC.getTransactionId(), e);
            abortTwoPhaseCommit(twoPC, "Prepare phase failed: " + e.getMessage());
        }
    }

    /**
     * Process vote from participant
     */
    public void processParticipantVote(String transactionId, String participantId, ParticipantVote vote) {
        DistributedTransactionState state = activeTransactions.get(transactionId);
        if (state == null) {
            log.warn("Received vote for unknown transaction: {}", transactionId);
            return;
        }

        if (state.getType() != TransactionType.TWO_PHASE_COMMIT) {
            log.warn("Received vote for non-2PC transaction: {}", transactionId);
            return;
        }

        log.debug("Received vote from participant {} for transaction {}: {}", 
                participantId, transactionId, vote.getDecision());

        state.getVotes().put(participantId, vote);
        updateTransactionState(state);

        // Check if all votes are collected
        if (state.getVotes().size() == state.getParticipants().size()) {
            processAllVotes(transactionId);
        }
    }

    /**
     * Process all collected votes
     */
    private void processAllVotes(String transactionId) {
        DistributedTransactionState state = activeTransactions.get(transactionId);
        if (state == null) {
            return;
        }

        boolean allCommit = state.getVotes().values().stream()
            .allMatch(vote -> vote.getDecision() == VoteDecision.COMMIT);

        if (allCommit) {
            log.info("All participants voted COMMIT for transaction: {}", transactionId);
            startCommitPhase(transactionId);
        } else {
            log.warn("Not all participants voted COMMIT for transaction: {}", transactionId);
            abortTwoPhaseCommit(createTwoPhaseCommitFromState(state), "Not all participants voted commit");
        }
    }

    /**
     * Start commit phase for two-phase commit
     */
    private void startCommitPhase(String transactionId) {
        DistributedTransactionState state = activeTransactions.get(transactionId);
        if (state == null) {
            return;
        }

        log.info("Starting commit phase for 2PC transaction: {}", transactionId);

        try {
            updateTransactionStatus(transactionId, TransactionStatus.COMMITTING);

            // Send commit messages to all participants
            for (TransactionParticipant participant : state.getParticipants()) {
                sendCommitMessage(transactionId, participant);
            }

            // Wait for commit confirmations (simplified - in practice would need proper handling)
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(5000); // Wait for commit confirmations
                    completeTwoPhaseCommit(transactionId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Interrupted while waiting for commit confirmations", e);
                }
            }, coordinatorExecutor);

        } catch (Exception e) {
            log.error("Failed to start commit phase for transaction: {}", transactionId, e);
            abortTwoPhaseCommit(createTwoPhaseCommitFromState(state), "Commit phase failed: " + e.getMessage());
        }
    }

    /**
     * Complete two-phase commit transaction
     */
    private void completeTwoPhaseCommit(String transactionId) {
        log.info("2PC transaction completed successfully: {}", transactionId);
        
        updateTransactionStatus(transactionId, TransactionStatus.COMPLETED);
        
        // Send completion event
        sendTransactionCompletionEvent(transactionId, TransactionStatus.COMPLETED);
        
        // Schedule cleanup
        scheduleTransactionCleanup(transactionId);
    }

    /**
     * Abort two-phase commit transaction
     */
    private void abortTwoPhaseCommit(TwoPhaseCommitTransaction twoPC, String reason) {
        log.warn("Aborting 2PC transaction: {} - Reason: {}", twoPC.getTransactionId(), reason);

        try {
            updateTransactionStatus(twoPC.getTransactionId(), TransactionStatus.ABORTING);

            DistributedTransactionState state = activeTransactions.get(twoPC.getTransactionId());
            if (state != null) {
                // Send abort messages to all participants
                for (TransactionParticipant participant : state.getParticipants()) {
                    sendAbortMessage(twoPC.getTransactionId(), participant);
                }
            }

            updateTransactionStatus(twoPC.getTransactionId(), TransactionStatus.ABORTED);
            
            // Send completion event
            sendTransactionCompletionEvent(twoPC.getTransactionId(), TransactionStatus.ABORTED);
            
            // Schedule cleanup
            scheduleTransactionCleanup(twoPC.getTransactionId());

        } catch (Exception e) {
            log.error("Error aborting 2PC transaction: {}", twoPC.getTransactionId(), e);
        }
    }

    /**
     * Recover orphaned transactions
     */
    @Scheduled(fixedRateString = "#{${transaction.coordinator.recovery.interval.minutes:5} * 60 * 1000}")
    public void recoverOrphanedTransactions() {
        if (!recoveryEnabled) {
            return;
        }

        log.debug("Starting orphaned transaction recovery");

        try {
            Instant cutoffTime = Instant.now().minus(Duration.ofMinutes(transactionTimeoutMinutes));
            
            List<String> orphanedTransactions = activeTransactions.entrySet().stream()
                .filter(entry -> entry.getValue().getStartTime().isBefore(cutoffTime))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

            for (String transactionId : orphanedTransactions) {
                log.warn("Recovering orphaned transaction: {}", transactionId);
                recoverTransaction(transactionId);
            }

        } catch (Exception e) {
            log.error("Error during orphaned transaction recovery", e);
        }
    }

    /**
     * Recover a specific transaction
     */
    private void recoverTransaction(String transactionId) {
        try {
            DistributedTransactionState state = activeTransactions.get(transactionId);
            if (state == null) {
                log.warn("Cannot recover transaction - state not found: {}", transactionId);
                return;
            }

            switch (state.getType()) {
                case SAGA:
                    recoverSagaTransaction(transactionId, state);
                    break;
                case TWO_PHASE_COMMIT:
                    recoverTwoPhaseCommitTransaction(transactionId, state);
                    break;
                default:
                    log.warn("Unknown transaction type for recovery: {}", state.getType());
            }

        } catch (Exception e) {
            log.error("Error recovering transaction: {}", transactionId, e);
        }
    }

    /**
     * Recover SAGA transaction
     */
    private void recoverSagaTransaction(String transactionId, DistributedTransactionState state) {
        log.info("Recovering SAGA transaction: {}", transactionId);

        switch (state.getStatus()) {
            case STARTED:
            case EXECUTING_STEP:
                // Restart from current step or start compensation
                if (state.getRetryCount() < maxRetryAttempts) {
                    state.setRetryCount(state.getRetryCount() + 1);
                    updateTransactionState(state);
                    
                    SagaTransaction saga = createSagaFromState(state);
                    executeNextSagaStep(saga);
                } else {
                    startSagaCompensation(createSagaFromState(state), "Max retries exceeded");
                }
                break;
                
            case COMPENSATING:
                // Continue compensation
                startSagaCompensation(createSagaFromState(state), "Recovery - continue compensation");
                break;
                
            default:
                // Transaction is in final state, clean up
                scheduleTransactionCleanup(transactionId);
        }
    }

    /**
     * Recover two-phase commit transaction
     */
    private void recoverTwoPhaseCommitTransaction(String transactionId, DistributedTransactionState state) {
        log.info("Recovering 2PC transaction: {}", transactionId);

        switch (state.getStatus()) {
            case STARTED:
            case PREPARING:
                // Restart prepare phase or abort
                if (state.getRetryCount() < maxRetryAttempts) {
                    state.setRetryCount(state.getRetryCount() + 1);
                    updateTransactionState(state);
                    
                    TwoPhaseCommitTransaction twoPC = createTwoPhaseCommitFromState(state);
                    startPreparePhase(twoPC);
                } else {
                    abortTwoPhaseCommit(createTwoPhaseCommitFromState(state), "Max retries exceeded");
                }
                break;
                
            case COMMITTING:
                // Continue commit phase
                startCommitPhase(transactionId);
                break;
                
            case ABORTING:
                // Continue abort
                abortTwoPhaseCommit(createTwoPhaseCommitFromState(state), "Recovery - continue abort");
                break;
                
            default:
                // Transaction is in final state, clean up
                scheduleTransactionCleanup(transactionId);
        }
    }

    // Helper methods

    private String generateTransactionId() {
        return "dtx_" + UUID.randomUUID().toString().replace("-", "");
    }

    private void storeTransactionState(DistributedTransactionState state) {
        try {
            String key = "dtx:state:" + state.getTransactionId();
            redisTemplate.opsForValue().set(key, state, Duration.ofHours(24));
        } catch (Exception e) {
            log.error("Failed to store transaction state", e);
        }
    }

    private void updateTransactionState(DistributedTransactionState state) {
        activeTransactions.put(state.getTransactionId(), state);
        storeTransactionState(state);
    }

    private void updateTransactionStatus(String transactionId, TransactionStatus status) {
        DistributedTransactionState state = activeTransactions.get(transactionId);
        if (state != null) {
            state.setStatus(status);
            state.setLastUpdated(Instant.now());
            updateTransactionState(state);
        }
    }

    private void cleanupTransactionState(String transactionId) {
        activeTransactions.remove(transactionId);
        try {
            String key = "dtx:state:" + transactionId;
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("Failed to cleanup transaction state", e);
        }
    }

    private void scheduleTransactionCleanup(String transactionId) {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(Duration.ofMinutes(5).toMillis());
                cleanupTransactionState(transactionId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, coordinatorExecutor);
    }

    private StepExecutionResult executeTransactionStep(TransactionStep step, String transactionId) {
        try {
            // Send step execution message
            StepExecutionMessage message = StepExecutionMessage.builder()
                .transactionId(transactionId)
                .stepId(step.getStepId())
                .stepType(step.getStepType())
                .parameters(step.getParameters())
                .build();

            kafkaTemplate.send("transaction-step-execution", transactionId, message);

            // For demo purposes, assume success
            return StepExecutionResult.success("Step executed successfully");

        } catch (Exception e) {
            log.error("Error executing transaction step", e);
            return StepExecutionResult.failure("Step execution failed: " + e.getMessage());
        }
    }

    private void sendPrepareMessage(String transactionId, TransactionParticipant participant) {
        try {
            PrepareMessage message = PrepareMessage.builder()
                .transactionId(transactionId)
                .participantId(participant.getParticipantId())
                .operation(participant.getOperation())
                .parameters(participant.getParameters())
                .build();

            kafkaTemplate.send("transaction-prepare", participant.getParticipantId(), message);
        } catch (Exception e) {
            log.error("Failed to send prepare message", e);
        }
    }

    private void sendCommitMessage(String transactionId, TransactionParticipant participant) {
        try {
            CommitMessage message = CommitMessage.builder()
                .transactionId(transactionId)
                .participantId(participant.getParticipantId())
                .build();

            kafkaTemplate.send("transaction-commit", participant.getParticipantId(), message);
        } catch (Exception e) {
            log.error("Failed to send commit message", e);
        }
    }

    private void sendAbortMessage(String transactionId, TransactionParticipant participant) {
        try {
            AbortMessage message = AbortMessage.builder()
                .transactionId(transactionId)
                .participantId(participant.getParticipantId())
                .build();

            kafkaTemplate.send("transaction-abort", participant.getParticipantId(), message);
        } catch (Exception e) {
            log.error("Failed to send abort message", e);
        }
    }

    private void scheduleVoteCollection(TwoPhaseCommitTransaction twoPC) {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(Duration.ofMinutes(1).toMillis()); // Wait for votes
                
                DistributedTransactionState state = activeTransactions.get(twoPC.getTransactionId());
                if (state != null && state.getStatus() == TransactionStatus.PREPARING) {
                    // Timeout waiting for votes, abort
                    abortTwoPhaseCommit(twoPC, "Vote collection timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, coordinatorExecutor);
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

    /**
     * Convert TransactionStep list to SagaStep list
     */
    private List<com.waqiti.common.saga.SagaStep> convertToSagaSteps(List<TransactionStep> steps) {
        return steps.stream()
            .map(step -> com.waqiti.common.saga.SagaStep.builder()
                .stepId(step.getStepId())
                .stepType(step.getStepType())
                .parameters(step.getParameters())
                .build())
            .collect(Collectors.toList());
    }

    private SagaTransaction createSagaFromState(DistributedTransactionState state) {
        return SagaTransaction.builder()
            .transactionId(state.getTransactionId())
            .steps(state.getSteps())
            .compensationSteps(state.getCompensationSteps())
            .currentStep(state.getCurrentStep())
            .coordinator(this)
            .build();
    }

    private TwoPhaseCommitTransaction createTwoPhaseCommitFromState(DistributedTransactionState state) {
        return TwoPhaseCommitTransaction.builder()
            .transactionId(state.getTransactionId())
            .participants(state.getParticipants())
            .votes(state.getVotes())
            .coordinator(this)
            .build();
    }

    // Data classes and enums

    @Data
    @Builder
    public static class DistributedTransactionState {
        private String transactionId;
        private TransactionType type;
        private TransactionStatus status;
        private Instant startTime;
        private Instant timeout;
        private Instant lastUpdated;
        private Map<String, Object> metadata;
        private int retryCount;
        
        // SAGA specific fields
        private List<TransactionStep> steps;
        private List<TransactionStep> compensationSteps;
        private int currentStep;
        
        // 2PC specific fields
        private List<TransactionParticipant> participants;
        private Map<String, ParticipantVote> votes;
    }

    public enum TransactionType {
        SAGA, TWO_PHASE_COMMIT
    }

    public enum TransactionStatus {
        STARTED, PREPARING, EXECUTING_STEP, COMMITTING, COMPENSATING,
        ABORTING, COMPLETED, COMPENSATED, ABORTED, COMPENSATION_FAILED,
        NOT_FOUND, COMMITTED
    }

    @Data
    @Builder
    public static class SagaTransactionRequest {
        private String description;
        private List<TransactionStep> steps;
        private Map<String, Object> metadata;
    }

    @Data
    @Builder
    public static class SagaTransaction {
        private String transactionId;
        private List<TransactionStep> steps;
        private List<TransactionStep> compensationSteps;
        private int currentStep;
        private DistributedTransactionCoordinator coordinator;
        private Object result;
    }

    @Data
    @Builder
    public static class TwoPhaseCommitRequest {
        private String description;
        private List<TransactionParticipant> participants;
        private Map<String, Object> metadata;
    }

    @Data
    @Builder
    public static class TwoPhaseCommitTransaction {
        private String transactionId;
        private List<TransactionParticipant> participants;
        private Map<String, ParticipantVote> votes;
        private DistributedTransactionCoordinator coordinator;
        private Object result;
    }

    @Data
    @Builder
    public static class TransactionStep {
        private String stepId;
        private String stepType;
        private Map<String, Object> parameters;
        private TransactionStep compensationStep;
    }

    @Data
    @Builder
    public static class TransactionParticipant {
        private String participantId;
        private String operation;
        private Map<String, Object> parameters;
    }

    @Data
    @Builder
    public static class ParticipantVote {
        private String participantId;
        private VoteDecision decision;
        private String reason;
        private Instant timestamp;
    }

    public enum VoteDecision {
        COMMIT, ABORT
    }

    @Data
    @Builder
    public static class StepExecutionResult {
        private boolean success;
        private String errorMessage;
        private Map<String, Object> resultData;

        public static StepExecutionResult success(String message) {
            return StepExecutionResult.builder().success(true).build();
        }

        public static StepExecutionResult failure(String errorMessage) {
            return StepExecutionResult.builder().success(false).errorMessage(errorMessage).build();
        }
    }

    @Data
    @Builder
    public static class StepExecutionMessage {
        private String transactionId;
        private String stepId;
        private String stepType;
        private Map<String, Object> parameters;
    }

    @Data
    @Builder
    public static class PrepareMessage {
        private String transactionId;
        private String participantId;
        private String operation;
        private Map<String, Object> parameters;
    }

    @Data
    @Builder
    public static class CommitMessage {
        private String transactionId;
        private String participantId;
    }

    @Data
    @Builder
    public static class AbortMessage {
        private String transactionId;
        private String participantId;
    }

    @Data
    @Builder
    public static class TransactionCompletionEvent {
        private String transactionId;
        private TransactionStatus status;
        private Instant timestamp;
    }

    public static class TransactionCoordinatorException extends RuntimeException {
        public TransactionCoordinatorException(String message) {
            super(message);
        }

        public TransactionCoordinatorException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}