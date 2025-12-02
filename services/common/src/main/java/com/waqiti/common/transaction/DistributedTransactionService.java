package com.waqiti.common.transaction;

import com.waqiti.common.saga.SagaStep;
import com.waqiti.common.transaction.model.SagaTransaction;
import com.waqiti.common.transaction.model.TwoPhaseCommitTransaction;
import com.waqiti.common.transaction.DistributedTransactionCoordinator.SagaTransactionRequest;
import com.waqiti.common.transaction.DistributedTransactionCoordinator.TwoPhaseCommitRequest;
import com.waqiti.common.transaction.DistributedTransactionCoordinator.DistributedTransactionState;
import com.waqiti.common.transaction.TransactionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * High-level distributed transaction service
 * Provides simplified API for distributed transactions
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedTransactionService {

    private final DistributedTransactionCoordinator coordinator;

    /**
     * Execute a SAGA transaction with compensation logic
     */
    public <T> CompletableFuture<T> executeSaga(
            String description,
            List<SagaStep> steps,
            Map<String, Object> metadata) {
        
        log.info("Executing SAGA transaction: {}", description);

        SagaTransactionRequest request = SagaTransactionRequest.builder()
            .description(description)
            .steps(convertSagaStepsToTransactionSteps(steps))
            .metadata(metadata)
            .build();
        
        try {
            SagaTransaction saga = coordinator.startSagaTransaction(request);
            
            return CompletableFuture.supplyAsync(() -> {
                try {
                    // Wait for saga completion
                    return waitForSagaCompletion(saga);
                } catch (Exception e) {
                    log.error("SAGA transaction failed: {}", saga.getTransactionId(), e);
                    throw new RuntimeException("SAGA transaction failed", e);
                }
            });
            
        } catch (Exception e) {
            log.error("Failed to start SAGA transaction: {}", description, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Execute a two-phase commit transaction
     */
    public <T> CompletableFuture<T> executeTwoPhaseCommit(
            String description,
            List<TransactionParticipant> participants,
            Map<String, Object> metadata) {
        
        log.info("Executing 2PC transaction: {}", description);

        TwoPhaseCommitRequest request = TwoPhaseCommitRequest.builder()
            .description(description)
            .participants(convertToCoordinatorParticipants(participants))
            .metadata(metadata)
            .build();
        
        try {
            TwoPhaseCommitTransaction twoPC = coordinator.startTwoPhaseCommit(request);
            
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return waitForTwoPhaseCommitCompletion(twoPC);
                } catch (Exception e) {
                    log.error("2PC transaction failed: {}", twoPC.getTransactionId(), e);
                    throw new RuntimeException("2PC transaction failed", e);
                }
            });
            
        } catch (Exception e) {
            log.error("Failed to start 2PC transaction: {}", description, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Execute a compensating transaction for rollback
     */
    public CompletableFuture<Void> executeCompensation(
            String originalTransactionId,
            List<CompensationStep> compensationSteps) {
        
        log.info("Executing compensation for transaction: {}", originalTransactionId);
        
        return CompletableFuture.runAsync(() -> {
            try {
                // Execute compensation steps in reverse order
                for (int i = compensationSteps.size() - 1; i >= 0; i--) {
                    CompensationStep step = compensationSteps.get(i);
                    
                    log.debug("Executing compensation step: {}", step.getName());
                    step.getCompensationAction().run();
                }
                
                log.info("Compensation completed for transaction: {}", originalTransactionId);
                
            } catch (Exception e) {
                log.error("Compensation failed for transaction: {}", originalTransactionId, e);
                throw new RuntimeException("Compensation failed", e);
            }
        });
    }

    /**
     * Check transaction status
     */
    public TransactionStatus getTransactionStatus(String transactionId) {
        DistributedTransactionState state = coordinator.getTransactionState(transactionId);
        return state != null ? convertToTransactionStatus(state.getStatus()) : TransactionStatus.NOT_FOUND;
    }

    /**
     * Wait for saga completion
     */
    @SuppressWarnings("unchecked")
    private <T> T waitForSagaCompletion(SagaTransaction saga) throws InterruptedException {
        while (true) {
            TransactionStatus status = getTransactionStatus(saga.getTransactionId());
            
            switch (status) {
                case COMMITTED:
                    log.info("SAGA transaction completed successfully: {}", saga.getTransactionId());
                    return (T) saga.getResult();
                    
                case ABORTED:
                case COMPENSATED:
                    throw new RuntimeException("SAGA transaction failed: " + saga.getTransactionId());
                    
                case NOT_FOUND:
                    throw new RuntimeException("SAGA transaction not found: " + saga.getTransactionId());
                    
                default:
                    Thread.sleep(1000); // Wait 1 second before checking again
                    break;
            }
        }
    }

    /**
     * Wait for two-phase commit completion
     */
    @SuppressWarnings("unchecked")
    private <T> T waitForTwoPhaseCommitCompletion(TwoPhaseCommitTransaction twoPC) throws InterruptedException {
        while (true) {
            TransactionStatus status = getTransactionStatus(twoPC.getTransactionId());
            
            switch (status) {
                case COMMITTED:
                    log.info("2PC transaction completed successfully: {}", twoPC.getTransactionId());
                    return (T) twoPC.getResult();
                    
                case ABORTED:
                    throw new RuntimeException("2PC transaction aborted: " + twoPC.getTransactionId());
                    
                case NOT_FOUND:
                    throw new RuntimeException("2PC transaction not found: " + twoPC.getTransactionId());
                    
                default:
                    Thread.sleep(1000); // Wait 1 second before checking again
                    break;
            }
        }
    }

    /**
     * Create a SAGA step builder for fluent API
     */
    public static SagaStepBuilder sagaStep(String name) {
        return new SagaStepBuilder(name);
    }

    /**
     * Create a compensation step builder
     */
    public static CompensationStepBuilder compensationStep(String name) {
        return new CompensationStepBuilder(name);
    }

    /**
     * Convert SagaStep list to TransactionStep list
     */
    private List<DistributedTransactionCoordinator.TransactionStep> convertSagaStepsToTransactionSteps(List<SagaStep> steps) {
        return steps.stream()
            .map(step -> DistributedTransactionCoordinator.TransactionStep.builder()
                .stepId(step.getStepId())
                .stepType(step.getStepType())
                .parameters(step.getParameters())
                .build())
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Convert TransactionParticipant list to Coordinator's TransactionParticipant list
     */
    private List<DistributedTransactionCoordinator.TransactionParticipant> convertToCoordinatorParticipants(List<TransactionParticipant> participants) {
        return participants.stream()
            .map(p -> DistributedTransactionCoordinator.TransactionParticipant.builder()
                .participantId(p.getParticipantId())
                .operation(p.getOperation())
                .parameters(p.getParameters())
                .build())
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Convert Coordinator's TransactionStatus to common TransactionStatus
     */
    private TransactionStatus convertToTransactionStatus(DistributedTransactionCoordinator.TransactionStatus coordinatorStatus) {
        try {
            return TransactionStatus.valueOf(coordinatorStatus.name());
        } catch (IllegalArgumentException e) {
            // If the status doesn't exist in the common enum, map it appropriately
            switch (coordinatorStatus) {
                case COMMITTED:
                    return TransactionStatus.COMPLETED;
                case STARTED:
                case PREPARING:
                case EXECUTING_STEP:
                case COMMITTING:
                    return TransactionStatus.PENDING;
                case ABORTED:
                    return TransactionStatus.FAILED;
                default:
                    return TransactionStatus.PENDING;
            }
        }
    }

    /**
     * Fluent builder for SAGA steps
     */
    public static class SagaStepBuilder {
        private final String name;
        // PRODUCTION FIX: Use correct SagaStep package (saga, not transaction.model)
        private java.util.function.Supplier<Object> action; // StepResult doesn't exist as inner class
        private java.util.function.Supplier<Object> compensationAction;
        private String targetService;
        private Map<String, Object> parameters;

        public SagaStepBuilder(String name) {
            this.name = name;
        }

        public SagaStepBuilder action(java.util.function.Supplier<Object> action) {
            this.action = action;
            return this;
        }

        public SagaStepBuilder compensation(java.util.function.Supplier<Object> compensationAction) {
            this.compensationAction = compensationAction;
            return this;
        }

        public SagaStepBuilder targetService(String targetService) {
            this.targetService = targetService;
            return this;
        }

        public SagaStepBuilder parameters(Map<String, Object> parameters) {
            this.parameters = parameters;
            return this;
        }

        public SagaStep build() {
            return SagaStep.builder()
                .name(name)
                .action(action != null ? action.get().toString() : null)
                .compensationAction(compensationAction != null ? compensationAction.get().toString() : null)
                .serviceName(targetService)
                .parameters(parameters)
                .build();
        }
    }

    /**
     * Fluent builder for compensation steps
     */
    public static class CompensationStepBuilder {
        private final String name;
        private Runnable compensationAction;
        private String description;

        public CompensationStepBuilder(String name) {
            this.name = name;
        }

        public CompensationStepBuilder action(Runnable compensationAction) {
            this.compensationAction = compensationAction;
            return this;
        }

        public CompensationStepBuilder description(String description) {
            this.description = description;
            return this;
        }

        public CompensationStep build() {
            return CompensationStep.builder()
                .name(name)
                .compensationAction(compensationAction)
                .description(description)
                .build();
        }
    }
}
