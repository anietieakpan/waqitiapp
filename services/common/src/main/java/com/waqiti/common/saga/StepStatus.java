package com.waqiti.common.saga;

/**
 * Status of an individual step
 */
public enum StepStatus {
    /**
     * Step is waiting to be executed
     */
    PENDING,
    
    /**
     * Step is currently running
     */
    RUNNING,
    
    /**
     * Step completed successfully
     */
    COMPLETED,
    
    /**
     * Step failed
     */
    FAILED,
    
    /**
     * Step was compensated
     */
    COMPENSATED,
    
    /**
     * Step was skipped
     */
    SKIPPED
}