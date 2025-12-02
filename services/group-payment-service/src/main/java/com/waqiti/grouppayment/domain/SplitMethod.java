package com.waqiti.grouppayment.domain;

/**
 * Enumeration of supported split bill calculation methods
 */
public enum SplitMethod {
    /**
     * Split equally among all participants
     */
    EQUAL,
    
    /**
     * Split by specified percentages
     */
    BY_PERCENTAGE,
    
    /**
     * Split by specific amounts per participant
     */
    BY_AMOUNT,
    
    /**
     * Split based on individual items consumed/ordered
     */
    BY_ITEM,
    
    /**
     * Split by weight or factor (useful for usage-based splits)
     */
    BY_WEIGHT,
    
    /**
     * Custom split with mixed calculation methods
     */
    CUSTOM
}