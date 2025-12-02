package com.waqiti.payment.saga.model;

/**
 * SAGA execution status
 */
public enum SagaStatus {
    STARTED,           // SAGA has been initiated
    IN_PROGRESS,       // SAGA steps are being executed
    COMPENSATING,      // Compensation is in progress
    COMPLETED,         // All steps completed successfully
    COMPENSATED,       // Compensation completed after failure
    FAILED,            // SAGA failed without compensation
    TIMED_OUT,         // SAGA execution timed out
    SUSPENDED,         // SAGA temporarily suspended
    CANCELLED          // SAGA cancelled by user/system
}