package com.waqiti.virtualcard.domain.enums;

/**
 * Card replacement reason enumeration
 */
public enum ReplacementReason {
    /**
     * Card was lost by the cardholder
     */
    LOST,
    
    /**
     * Card was stolen
     */
    STOLEN,
    
    /**
     * Card was physically damaged
     */
    DAMAGED,
    
    /**
     * Card was defective from manufacturing
     */
    DEFECTIVE,
    
    /**
     * Cardholder name changed
     */
    NAME_CHANGE,
    
    /**
     * Card expired
     */
    EXPIRED,
    
    /**
     * Security concerns
     */
    SECURITY,
    
    /**
     * Upgrade to different card type
     */
    UPGRADE
}