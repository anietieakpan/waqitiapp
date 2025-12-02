package com.waqiti.saga.step;

import com.waqiti.saga.domain.SagaExecution;

/**
 * Base interface for all saga steps
 * 
 * Each step represents a single action in a saga that can be executed
 * and potentially compensated if the saga fails.
 */
public interface SagaStep {
    
    /**
     * Get the unique name of this step
     */
    String getStepName();
    
    /**
     * Execute this step
     * 
     * @param execution The saga execution context
     * @return The result of step execution
     */
    StepExecutionResult execute(SagaExecution execution);
    
    /**
     * Check if this step is compensatable
     * 
     * @return true if this step has compensation logic
     */
    default boolean isCompensatable() {
        return false;
    }
    
    /**
     * Get timeout for this step in seconds
     * 
     * @return timeout in seconds
     */
    default int getTimeout() {
        return 30; // Default 30 seconds
    }
}