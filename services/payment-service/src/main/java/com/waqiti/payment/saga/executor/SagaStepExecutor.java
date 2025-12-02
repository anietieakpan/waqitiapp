package com.waqiti.payment.saga.executor;

import com.waqiti.payment.saga.model.StepContext;
import com.waqiti.payment.saga.model.StepResult;

/**
 * Interface for executing individual SAGA steps
 * Each step type should have its own implementation
 */
public interface SagaStepExecutor {
    
    /**
     * Execute a SAGA step with the given context
     * 
     * @param context The step execution context containing all necessary data
     * @return StepResult indicating success/failure and any data produced
     */
    StepResult execute(StepContext context);
    
    /**
     * Compensate a previously executed step
     * 
     * @param context The step execution context
     * @return StepResult indicating success/failure of compensation
     */
    StepResult compensate(StepContext context);
    
    /**
     * Get the type of step this executor handles
     * 
     * @return The step type identifier
     */
    String getStepType();
    
    /**
     * Check if this step can be retried on failure
     * 
     * @return true if the step is idempotent and can be safely retried
     */
    default boolean isRetryable() {
        return true;
    }
    
    /**
     * Get the maximum number of retry attempts for this step
     * 
     * @return Maximum retry attempts (default: 3)
     */
    default int getMaxRetryAttempts() {
        return 3;
    }
    
    /**
     * Get the timeout duration for this step in seconds
     * 
     * @return Timeout in seconds (default: 30)
     */
    default int getTimeoutSeconds() {
        return 30;
    }
}