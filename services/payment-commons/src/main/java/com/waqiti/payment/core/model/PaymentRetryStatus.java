package com.waqiti.payment.core.model;

/**
 * Payment Retry Status Enumeration
 * 
 * Defines the various states a payment retry operation can be in.
 * 
 * @author Waqiti Payment Team
 * @version 3.0.0
 * @since 2025-01-16
 */
public enum PaymentRetryStatus {
    
    /**
     * Retry has been scheduled and is waiting for execution
     */
    SCHEDULED,
    
    /**
     * Retry is currently being executed
     */
    IN_PROGRESS,
    
    /**
     * Retry completed successfully
     */
    COMPLETED,
    
    /**
     * Payment/error is not eligible for retry
     */
    NOT_RETRYABLE,
    
    /**
     * Maximum retry attempts have been reached
     */
    MAX_ATTEMPTS_REACHED,
    
    /**
     * Retry operation itself failed
     */
    RETRY_FAILED,
    
    /**
     * Retry was cancelled before execution
     */
    CANCELLED,
    
    /**
     * Retry timed out waiting for execution
     */
    TIMEOUT,
    
    /**
     * Retry was deferred to later time
     */
    DEFERRED;
    
    /**
     * Check if this status indicates retry is active
     */
    public boolean isActive() {
        return this == SCHEDULED || this == IN_PROGRESS;
    }
    
    /**
     * Check if this status indicates retry has finished (successfully or not)
     */
    public boolean isTerminal() {
        return this == COMPLETED || 
               this == NOT_RETRYABLE || 
               this == MAX_ATTEMPTS_REACHED || 
               this == RETRY_FAILED || 
               this == CANCELLED || 
               this == TIMEOUT;
    }
    
    /**
     * Check if this status indicates retry completed successfully
     */
    public boolean isSuccessful() {
        return this == COMPLETED;
    }
    
    /**
     * Check if this status indicates retry failed
     */
    public boolean isFailed() {
        return this == NOT_RETRYABLE || 
               this == MAX_ATTEMPTS_REACHED || 
               this == RETRY_FAILED || 
               this == TIMEOUT;
    }
    
    /**
     * Check if retry can be cancelled in this status
     */
    public boolean isCancellable() {
        return this == SCHEDULED || this == DEFERRED;
    }
}