package com.waqiti.payment.entity;

/**
 * Enumeration representing the status of an NFC device
 */
public enum NFCDeviceStatus {
    
    /**
     * Device is pending initial verification
     */
    PENDING,
    
    /**
     * Device is active and can process transactions
     */
    ACTIVE,
    
    /**
     * Device is temporarily suspended
     */
    SUSPENDED,
    
    /**
     * Device is blocked due to security concerns
     */
    BLOCKED,
    
    /**
     * Device registration has expired
     */
    EXPIRED,
    
    /**
     * Device has been deactivated by user
     */
    DEACTIVATED,
    
    /**
     * Device is under review for suspicious activity
     */
    UNDER_REVIEW,
    
    /**
     * Device requires re-authentication
     */
    REQUIRES_AUTH,
    
    /**
     * Device has been compromised
     */
    COMPROMISED
}