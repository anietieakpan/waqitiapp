package com.waqiti.dispute.entity;

/**
 * Status values for DLQ entries
 */
public enum DLQStatus {
    PENDING_REVIEW,      // Awaiting manual review
    RETRY_SCHEDULED,     // Scheduled for automatic retry
    RETRYING,            // Currently being retried
    RESOLVED,            // Successfully recovered
    PERMANENT_FAILURE,   // Cannot be recovered, manual intervention required
    DISCARDED,           // Intentionally discarded with audit trail
    ESCALATED            // Escalated to emergency response team
}
