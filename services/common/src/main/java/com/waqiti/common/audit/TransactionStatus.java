package com.waqiti.common.audit;

/**
 * Transaction Status Enumeration for Audit Purposes
 * 
 * Represents transaction states for audit logging.
 * This is a subset of the full transaction statuses used specifically for audit trails.
 */
public enum TransactionStatus {
    
    /**
     * Transaction has been initiated
     */
    INITIATED,
    
    /**
     * Transaction is being processed
     */
    PROCESSING,
    
    /**
     * Transaction is pending confirmation
     */
    PENDING,
    
    /**
     * Transaction has been successfully completed
     */
    COMPLETED,
    
    /**
     * Transaction has failed
     */
    FAILED,
    
    /**
     * Transaction has been cancelled
     */
    CANCELLED,
    
    /**
     * Transaction has been reversed
     */
    REVERSED,
    
    /**
     * Transaction has expired
     */
    EXPIRED
}