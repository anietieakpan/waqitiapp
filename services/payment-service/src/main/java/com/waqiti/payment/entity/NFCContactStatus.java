package com.waqiti.payment.entity;

/**
 * Enumeration representing the status of an NFC contact relationship
 */
public enum NFCContactStatus {
    
    /**
     * Contact invitation is pending acceptance
     */
    PENDING,
    
    /**
     * Contact relationship is active
     */
    ACTIVE,
    
    /**
     * Contact has been blocked by the user
     */
    BLOCKED,
    
    /**
     * Contact relationship has been removed/deleted
     */
    REMOVED,
    
    /**
     * Contact is temporarily suspended
     */
    SUSPENDED,
    
    /**
     * Contact invitation was declined
     */
    DECLINED,
    
    /**
     * Contact invitation has expired
     */
    EXPIRED,
    
    /**
     * Contact is under review for suspicious activity
     */
    UNDER_REVIEW
}