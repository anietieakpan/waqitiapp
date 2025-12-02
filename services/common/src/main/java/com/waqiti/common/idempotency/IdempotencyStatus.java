package com.waqiti.common.idempotency;

/**
 * ENHANCED: Status of an idempotent operation with complete lifecycle support
 * Critical for financial transaction integrity and audit compliance
 */
public enum IdempotencyStatus {
    
    /**
     * Operation is currently being processed
     */
    IN_PROGRESS,
    
    /**
     * Operation completed successfully
     */
    COMPLETED,
    
    /**
     * Operation failed and will not be retried
     */
    FAILED,
    
    /**
     * Operation failed but can be retried
     */
    RETRYABLE_FAILED,
    
    /**
     * Idempotency record has expired
     */
    EXPIRED,
    
    /**
     * Operation was cancelled by user or system
     */
    CANCELLED,
    
    /**
     * Operation is pending approval (e.g., manual review)
     */
    PENDING_APPROVAL,
    
    /**
     * Operation was rejected during approval
     */
    REJECTED;
    
    /**
     * Check if status indicates operation is still active
     */
    public boolean isActive() {
        return this == IN_PROGRESS || this == PENDING_APPROVAL;
    }
    
    /**
     * Check if status indicates operation is final (no further changes)
     */
    public boolean isFinal() {
        return this == COMPLETED || this == FAILED || this == EXPIRED || 
               this == CANCELLED || this == REJECTED;
    }
    
    /**
     * Check if operation can be retried
     */
    public boolean isRetryable() {
        return this == RETRYABLE_FAILED;
    }
    
    /**
     * Check if operation was successful
     */
    public boolean isSuccessful() {
        return this == COMPLETED;
    }
}