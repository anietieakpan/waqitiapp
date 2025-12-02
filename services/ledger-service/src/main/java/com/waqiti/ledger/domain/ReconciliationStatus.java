package com.waqiti.ledger.domain;

/**
 * Reconciliation Status Enum
 *
 * Defines the lifecycle status of a reconciliation process.
 */
public enum ReconciliationStatus {
    /**
     * Reconciliation has been initiated but not started
     */
    PENDING,

    /**
     * Reconciliation is currently in progress
     */
    IN_PROGRESS,

    /**
     * Reconciliation completed successfully with no discrepancies
     */
    COMPLETED,

    /**
     * Reconciliation completed with discrepancies found
     */
    COMPLETED_WITH_DISCREPANCIES,

    /**
     * Reconciliation failed due to errors
     */
    FAILED,

    /**
     * Reconciliation requires manual review
     */
    MANUAL_REVIEW_REQUIRED,

    /**
     * Reconciliation has been cancelled
     */
    CANCELLED,

    /**
     * Reconciliation is on hold
     */
    ON_HOLD
}
