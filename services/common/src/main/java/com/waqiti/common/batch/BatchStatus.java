package com.waqiti.common.batch;

/**
 * Batch processing status enumeration
 */
public enum BatchStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    TIMEOUT;
    
    public boolean isComplete() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == TIMEOUT;
    }
    
    public boolean isRunning() {
        return this == RUNNING;
    }
    
    public boolean isSuccessful() {
        return this == COMPLETED;
    }
}