package com.waqiti.accounting.domain;

/**
 * DLQ Message Status Enum
 */
public enum DlqStatus {
    /**
     * Message is pending retry
     */
    PENDING,

    /**
     * Message is currently being retried
     */
    RETRYING,

    /**
     * Message has been successfully processed after retry
     */
    RESOLVED,

    /**
     * Message has exhausted all retry attempts
     */
    FAILED,

    /**
     * Message requires manual review and intervention
     */
    MANUAL_REVIEW
}
