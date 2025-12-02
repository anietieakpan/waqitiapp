package com.waqiti.transaction.domain;

/**
 * Transaction Status Enumeration
 * 
 * Represents all possible states of a transaction in its lifecycle.
 * The state transitions follow a defined state machine pattern.
 */
public enum TransactionStatus {
    
    /**
     * Transaction has been created but not yet validated
     */
    INITIATED,
    
    /**
     * Transaction is being validated (balance check, limits, etc.)
     */
    VALIDATING,
    
    /**
     * Transaction has been validated and is ready for processing
     */
    VALIDATED,
    
    /**
     * Transaction is currently being processed
     */
    PROCESSING,
    
    /**
     * Transaction is pending external confirmation (e.g., bank transfer)
     */
    PENDING_CONFIRMATION,
    
    /**
     * Transaction has been successfully completed
     */
    COMPLETED,
    
    /**
     * Transaction has failed during processing
     */
    FAILED,
    
    /**
     * Transaction encountered an error during processing
     */
    PROCESSING_ERROR,
    
    /**
     * Transaction has been cancelled by user or system
     */
    CANCELLED,
    
    /**
     * Transaction has been reversed after completion
     */
    REVERSED,
    
    /**
     * Transaction is on hold for manual review
     */
    ON_HOLD,
    
    /**
     * Transaction has expired (timeout)
     */
    EXPIRED,
    
    /**
     * Transaction is waiting for approval
     */
    PENDING,
    
    /**
     * Transaction has timed out
     */
    TIMEOUT,
    
    /**
     * Transaction has been rolled back due to batch failure
     */
    ROLLED_BACK,
    
    /**
     * Transaction failed permanently after max retries
     */
    PERMANENTLY_FAILED,
    
    /**
     * Transaction was retried successfully
     */
    RETRIED_SUCCESSFUL,
    
    /**
     * Transaction has been suspended due to fraud or compliance concerns
     */
    SUSPENDED,
    
    /**
     * Transaction has been frozen as part of account freeze
     */
    FROZEN
}