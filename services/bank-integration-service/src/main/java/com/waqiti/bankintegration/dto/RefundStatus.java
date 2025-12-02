package com.waqiti.bankintegration.dto;

/**
 * Refund Status Enum
 * 
 * Represents the various states a refund can be in
 */
public enum RefundStatus {
    /**
     * Refund has been initiated
     */
    INITIATED,
    
    /**
     * Refund is pending processing
     */
    PENDING,
    
    /**
     * Refund is being processed
     */
    PROCESSING,
    
    /**
     * Refund has been completed successfully
     */
    COMPLETED,
    
    /**
     * Refund was successful (alias for COMPLETED)
     */
    SUCCESS,
    
    /**
     * Refund has failed
     */
    FAILED,
    
    /**
     * Refund has been cancelled
     */
    CANCELLED,
    
    /**
     * Refund requires manual review
     */
    REQUIRES_ACTION
}