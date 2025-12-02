package com.waqiti.common.transaction;

/**
 * Status of a distributed transaction
 */
public enum TransactionStatus {
    /**
     * Transaction is active and participants can be enlisted
     */
    ACTIVE,
    
    /**
     * Transaction is in the prepare phase (Phase 1 of 2PC)
     */
    PREPARING,
    
    /**
     * All participants have prepared successfully
     */
    PREPARED,
    
    /**
     * Prepare phase failed, transaction will be aborted
     */
    PREPARE_FAILED,
    
    /**
     * Transaction is in the commit phase (Phase 2 of 2PC)
     */
    COMMITTING,
    
    /**
     * Transaction has been committed successfully
     */
    COMMITTED,
    
    /**
     * Commit phase failed, partial commit occurred
     */
    COMMIT_FAILED,
    
    /**
     * Transaction is being aborted/rolled back
     */
    ABORTING,
    
    /**
     * Transaction has been aborted/rolled back successfully
     */
    ABORTED,
    
    /**
     * Transaction failed and could not be rolled back properly
     */
    FAILED,
    
    /**
     * Transaction status is unknown (possibly due to timeout or network issues)
     */
    UNKNOWN,
    
    /**
     * Transaction has timed out
     */
    TIMEOUT,
    
    /**
     * Transaction initiated but not yet started
     */
    INITIATED,
    
    /**
     * Executing compensations (saga pattern)
     */
    COMPENSATING,
    
    /**
     * Compensations completed (saga pattern)
     */
    COMPENSATED,
    
    /**
     * Transaction temporarily suspended
     */
    SUSPENDED,
    
    /**
     * Transaction resumed after suspension
     */
    RESUMED,

    /**
     * Transaction not found
     */
    NOT_FOUND,

    /**
     * Transaction pending - alias for ACTIVE/INITIATED for compatibility
     */
    PENDING,

    /**
     * Transaction completed successfully - alias for COMMITTED for compatibility
     */
    COMPLETED
}