package com.waqiti.common.messaging.recovery.model;

/**
 * Represents the possible statuses of a message in the Dead Letter Queue (DLQ) recovery process.
 */
public enum DLQStatus {
    PENDING,          // Awaiting processing
    IN_PROGRESS,      // Currently being retried or recovered
    RECOVERED,        // Successfully processed from DLQ
    FAILED,           // Recovery failed permanently
    MANUAL_REVIEW,    // Requires manual inspection
    DEAD_STORAGE,     // Archived for audit/compliance purposes
    SCHEDULED         // Scheduled for later retry or review
}
