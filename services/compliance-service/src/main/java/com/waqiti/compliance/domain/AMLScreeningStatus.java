package com.waqiti.compliance.domain;

/**
 * Status values for AML screening operations.
 * Tracks the lifecycle of an AML screening from initiation to completion.
 */
public enum AMLScreeningStatus {

    /**
     * Screening has been initiated but not yet started
     */
    INITIATED,

    /**
     * Screening is currently in progress
     */
    IN_PROGRESS,

    /**
     * Screening has been completed
     */
    COMPLETED,

    /**
     * Screening results require manual review
     */
    UNDER_REVIEW,

    /**
     * Screening has been approved after review
     */
    APPROVED,

    /**
     * Screening has been rejected (entity blocked)
     */
    REJECTED,

    /**
     * Screening has been escalated to higher authority
     */
    ESCALATED,

    /**
     * Screening failed due to technical error
     */
    FAILED,

    /**
     * Screening is pending additional information
     */
    PENDING_INFO,

    /**
     * Screening has been cancelled
     */
    CANCELLED,

    /**
     * Screening is on hold pending external data
     */
    ON_HOLD,

    /**
     * Entity/transaction blocked due to AML screening results
     */
    BLOCKED,

    /**
     * Real-time screening completed (fast-track)
     */
    REALTIME_COMPLETED,

    /**
     * Screening is pending manual review
     */
    PENDING_REVIEW
}
