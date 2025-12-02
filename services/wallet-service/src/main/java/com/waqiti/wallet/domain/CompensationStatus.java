package com.waqiti.wallet.domain;

/**
 * Compensation Status Enumeration
 * 
 * Represents the various states of a compensation record
 */
public enum CompensationStatus {
    /**
     * Initial state - compensation is pending execution
     */
    PENDING,
    
    /**
     * Compensation is currently being processed
     */
    IN_PROGRESS,
    
    /**
     * Compensation completed successfully
     */
    COMPLETED,
    
    /**
     * Compensation failed but will be retried
     */
    RETRY,
    
    /**
     * Compensation failed after all retry attempts
     */
    FAILED,
    
    /**
     * Compensation failed permanently and cannot be retried
     */
    FAILED_PERMANENTLY,
    
    /**
     * Requires manual review by operations team
     */
    MANUAL_REVIEW,
    
    /**
     * Compensation was cancelled
     */
    CANCELLED,
    
    /**
     * Compensation was partially completed
     */
    PARTIALLY_COMPLETED,
    
    /**
     * Compensation is on hold pending investigation
     */
    ON_HOLD
}