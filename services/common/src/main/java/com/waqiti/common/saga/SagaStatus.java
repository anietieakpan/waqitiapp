package com.waqiti.common.saga;

/**
 * Saga Status Enumeration
 * 
 * Represents the current state of a saga execution.
 * The status transitions follow a defined state machine pattern.
 */
public enum SagaStatus {
    
    /**
     * Saga has been created but not yet started
     */
    INITIATED,
    
    /**
     * Saga is currently executing its steps
     */
    RUNNING,
    
    /**
     * Saga has completed successfully
     */
    COMPLETED,
    
    /**
     * Saga has failed and is executing compensation actions
     */
    COMPENSATING,
    
    /**
     * Saga has been successfully compensated (rolled back)
     */
    COMPENSATED,
    
    /**
     * Saga has failed and cannot be compensated
     */
    FAILED,
    
    /**
     * Saga compensation has failed
     */
    COMPENSATION_FAILED,
    
    /**
     * Saga execution has timed out
     */
    TIMED_OUT,
    
    /**
     * Saga has been cancelled by user or system
     */
    CANCELLED,
    
    /**
     * Saga is paused waiting for external input
     */
    PAUSED,
    
    /**
     * Saga is suspended due to system issues
     */
    SUSPENDED,
    
    /**
     * Saga was not found in the system
     */
    NOT_FOUND;
    
    /**
     * Check if this status represents a terminal state
     * @return true if the saga is in a terminal state
     */
    public boolean isTerminal() {
        return this == COMPLETED || 
               this == COMPENSATED || 
               this == FAILED || 
               this == COMPENSATION_FAILED || 
               this == TIMED_OUT || 
               this == CANCELLED;
    }
}