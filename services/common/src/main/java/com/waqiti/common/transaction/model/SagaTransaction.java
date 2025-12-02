package com.waqiti.common.transaction.model;

import com.waqiti.common.saga.SagaStep;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Enterprise-grade Saga Transaction model for distributed transaction management.
 * Implements the Saga pattern for managing long-running business transactions
 * across multiple services with compensating actions.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SagaTransaction implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Unique transaction identifier
     */
    @NotNull
    @Builder.Default
    private String transactionId = UUID.randomUUID().toString();
    
    /**
     * Parent transaction ID for nested sagas
     */
    private String parentTransactionId;
    
    /**
     * Human-readable transaction name
     */
    @NotNull(message = "Transaction name is required")
    @Size(min = 1, max = 200)
    private String name;
    
    /**
     * Transaction description
     */
    private String description;
    
    /**
     * Transaction type/category
     */
    private String transactionType;
    
    /**
     * Current state of the saga
     */
    @Builder.Default
    private SagaState state = SagaState.CREATED;
    
    /**
     * List of steps in this saga
     */
    @Builder.Default
    private List<SagaStep> steps = new ArrayList<>();
    
    /**
     * Index of current step being executed
     */
    @Builder.Default
    private int currentStepIndex = -1;
    
    /**
     * Shared data across all steps
     */
    @Builder.Default
    private Map<String, Object> sharedContext = new HashMap<>();
    
    /**
     * Transaction metadata
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    
    /**
     * User who initiated the transaction
     */
    private String initiatedBy;
    
    /**
     * Timestamp when transaction was created
     */
    @NotNull
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    /**
     * Timestamp when transaction started execution
     */
    private LocalDateTime startedAt;
    
    /**
     * Timestamp when transaction completed
     */
    private LocalDateTime completedAt;
    
    /**
     * Last update timestamp
     */
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    /**
     * Error message if transaction failed
     */
    private String errorMessage;
    
    /**
     * Stack trace if transaction failed
     */
    private String errorStackTrace;
    
    /**
     * Number of retry attempts for the entire saga
     */
    @Builder.Default
    private int retryCount = 0;
    
    /**
     * Maximum retry attempts allowed
     */
    @Builder.Default
    private int maxRetries = 3;
    
    /**
     * Compensation strategy
     */
    @Builder.Default
    private CompensationStrategy compensationStrategy = CompensationStrategy.BACKWARD;
    
    /**
     * Transaction isolation level
     */
    @Builder.Default
    private IsolationLevel isolationLevel = IsolationLevel.READ_COMMITTED;
    
    /**
     * Transaction timeout in seconds
     */
    @Builder.Default
    private long timeoutSeconds = 3600; // 1 hour default
    
    /**
     * Whether transaction is synchronous or asynchronous
     */
    @Builder.Default
    private boolean asynchronous = true;
    
    /**
     * Transaction result data
     */
    private Map<String, Object> result;

    /**
     * Get transaction result (for compatibility with coordinator)
     */
    public Object getResult() {
        return result;
    }
    
    /**
     * Audit trail of state changes
     */
    @Builder.Default
    private List<StateTransition> stateHistory = new ArrayList<>();
    
    /**
     * Performance metrics
     */
    @Builder.Default
    private TransactionMetrics metrics = new TransactionMetrics();
    
    /**
     * Enum for saga states
     */
    public enum SagaState {
        CREATED("Saga has been created but not started"),
        STARTING("Saga is initializing"),
        RUNNING("Saga is executing steps"),
        COMPENSATING("Saga is rolling back due to failure"),
        COMPLETED("Saga completed successfully"),
        FAILED("Saga failed and could not be compensated"),
        COMPENSATED("Saga was successfully rolled back"),
        SUSPENDED("Saga execution is suspended"),
        CANCELLED("Saga was cancelled by user");
        
        private final String description;
        
        SagaState(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
        
        public boolean isTerminal() {
            return this == COMPLETED || this == FAILED || 
                   this == COMPENSATED || this == CANCELLED;
        }
    }
    
    /**
     * Compensation strategy enum
     */
    public enum CompensationStrategy {
        BACKWARD("Compensate in reverse order"),
        FORWARD("Continue forward with alternative path"),
        PARALLEL("Compensate all steps in parallel"),
        CUSTOM("Custom compensation logic");
        
        private final String description;
        
        CompensationStrategy(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Transaction isolation level
     */
    public enum IsolationLevel {
        READ_UNCOMMITTED("Lowest isolation level"),
        READ_COMMITTED("Default isolation level"),
        REPEATABLE_READ("Prevents non-repeatable reads"),
        SERIALIZABLE("Highest isolation level");
        
        private final String description;
        
        IsolationLevel(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * State transition record
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StateTransition {
        private SagaState fromState;
        private SagaState toState;
        private LocalDateTime timestamp;
        private String reason;
        private String triggeredBy;
    }
    
    /**
     * Transaction performance metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionMetrics {
        @Builder.Default
        private long totalExecutionTimeMs = 0;
        @Builder.Default
        private long compensationTimeMs = 0;
        @Builder.Default
        private int stepsCompleted = 0;
        @Builder.Default
        private int stepsCompensated = 0;
        @Builder.Default
        private int stepsFailed = 0;
        @Builder.Default
        private Map<String, Long> stepExecutionTimes = new HashMap<>();
    }
    
    /**
     * Add a step to the saga
     */
    public void addStep(SagaStep step) {
        if (steps == null) {
            steps = new ArrayList<>();
        }
        steps.add(step);
    }
    
    /**
     * Get current step
     */
    public SagaStep getCurrentStep() {
        if (currentStepIndex >= 0 && currentStepIndex < steps.size()) {
            return steps.get(currentStepIndex);
        }
        return null;
    }
    
    /**
     * Check if saga is in terminal state
     */
    public boolean isTerminal() {
        return state.isTerminal();
    }
    
    /**
     * Check if saga can be retried
     */
    public boolean canRetry() {
        return !isTerminal() && retryCount < maxRetries;
    }
    
    /**
     * Calculate total execution time
     */
    public Long getTotalExecutionTime() {
        if (startedAt != null && completedAt != null) {
            return java.time.Duration.between(startedAt, completedAt).toMillis();
        }
        return null;
    }
    
    /**
     * Add state transition to history
     */
    public void addStateTransition(SagaState newState, String reason, String triggeredBy) {
        if (stateHistory == null) {
            stateHistory = new ArrayList<>();
        }
        stateHistory.add(StateTransition.builder()
                .fromState(this.state)
                .toState(newState)
                .timestamp(LocalDateTime.now())
                .reason(reason)
                .triggeredBy(triggeredBy)
                .build());
        this.state = newState;
        this.updatedAt = LocalDateTime.now();
    }
}