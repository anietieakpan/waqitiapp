package com.waqiti.dispute.entity;

/**
 * Recovery strategies for DLQ entries
 */
public enum RecoveryStrategy {
    RETRY_WITH_BACKOFF,      // Retry with exponential backoff
    TRANSFORM_AND_RETRY,     // Transform data and retry
    MANUAL_INTERVENTION,     // Requires manual review and action
    COMPENSATE,              // Execute compensation/reversal logic
    DISCARD_WITH_AUDIT,      // Discard but maintain full audit trail
    ESCALATE_TO_EMERGENCY    // Critical - page on-call engineer
}
