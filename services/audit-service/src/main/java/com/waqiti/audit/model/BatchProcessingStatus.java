package com.waqiti.audit.model;

/**
 * Status enumeration for batch processing operations
 */
public enum BatchProcessingStatus {
    SUCCESS,
    FAILED,
    PARTIAL_SUCCESS,
    IN_PROGRESS,
    CANCELLED,
    TIMEOUT
}