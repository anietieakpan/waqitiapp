package com.waqiti.payment.entity;

/**
 * ACH Batch Status Enum
 *
 * Lifecycle states for ACH batch processing per NACHA rules
 *
 * @author Waqiti Engineering
 */
public enum ACHBatchStatus {
    /**
     * Batch created but not yet ready for processing
     */
    PENDING,

    /**
     * Batch is being validated (pre-processing checks)
     */
    VALIDATING,

    /**
     * Batch validation failed
     */
    VALIDATION_FAILED,

    /**
     * Batch is ready for processing
     */
    READY,

    /**
     * Batch is currently being processed
     */
    PROCESSING,

    /**
     * Batch has been submitted to ACH network
     */
    SUBMITTED,

    /**
     * Batch is in settlement (waiting for clearing)
     */
    SETTLING,

    /**
     * Batch has been successfully settled
     */
    SETTLED,

    /**
     * Batch processing failed
     */
    FAILED,

    /**
     * Batch was rejected by ACH network
     */
    REJECTED,

    /**
     * Batch was cancelled before submission
     */
    CANCELLED,

    /**
     * Batch returned by receiving bank
     */
    RETURNED,

    /**
     * Batch contains corrections (NOC - Notification of Change)
     */
    CORRECTED,

    /**
     * Batch is on hold (manual review required)
     */
    ON_HOLD
}
