package com.waqiti.user.saga;

/**
 * Saga Status
 *
 * Lifecycle states of a saga execution
 */
public enum SagaStatus {
    /**
     * Saga has been initiated
     */
    STARTED,

    /**
     * Saga steps are executing
     */
    IN_PROGRESS,

    /**
     * All steps completed successfully
     */
    COMPLETED,

    /**
     * Saga failed, compensation in progress
     */
    COMPENSATING,

    /**
     * Compensation completed successfully (rollback done)
     */
    COMPENSATED,

    /**
     * Compensation failed - MANUAL INTERVENTION REQUIRED
     */
    COMPENSATION_FAILED,

    /**
     * Saga failed and could not be compensated
     */
    FAILED
}
