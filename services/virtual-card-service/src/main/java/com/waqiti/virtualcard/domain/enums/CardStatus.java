package com.waqiti.virtualcard.domain.enums;

/**
 * Card status enumeration
 */
public enum CardStatus {
    /**
     * Card has been ordered but not yet in production
     */
    ORDERED,
    
    /**
     * Card is being manufactured
     */
    IN_PRODUCTION,
    
    /**
     * Card has been shipped but not delivered
     */
    SHIPPED,
    
    /**
     * Card has been delivered but not activated
     */
    DELIVERED,
    
    /**
     * Card is active and can be used
     */
    ACTIVE,
    
    /**
     * Card is temporarily blocked
     */
    BLOCKED,
    
    /**
     * Card has been replaced with a new one
     */
    REPLACED,
    
    /**
     * Card has been permanently closed
     */
    CLOSED,
    
    /**
     * Card activation failed or was rejected
     */
    FAILED,
    
    /**
     * Card has expired
     */
    EXPIRED
}