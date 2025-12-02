package com.waqiti.security.model;

/**
 * Processing Status Enum
 * Represents the status of anomaly processing
 */
public enum ProcessingStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    RETRYING,
    PARTIAL_SUCCESS
}
