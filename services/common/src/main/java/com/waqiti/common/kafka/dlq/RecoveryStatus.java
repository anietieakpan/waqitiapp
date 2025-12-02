package com.waqiti.common.kafka.dlq;

/**
 * Status of DLQ message recovery.
 */
public enum RecoveryStatus {
    PENDING_RETRY,
    RETRY_IN_PROGRESS,
    RECOVERED,
    MANUAL_INTERVENTION_REQUIRED,
    MANUAL_RETRY_IN_PROGRESS,
    ABANDONED
}
