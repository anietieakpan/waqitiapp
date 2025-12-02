package com.waqiti.payment.core.model;

/**
 * Fraud detection recommendation enumeration
 */
public enum FraudRecommendation {
    ALLOW("Allow transaction to proceed"),
    MONITOR("Allow but monitor closely"),
    REVIEW("Hold for manual review"),
    BLOCK("Block transaction immediately");
    
    private final String description;
    
    FraudRecommendation(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean allowsPayment() {
        return this == ALLOW || this == MONITOR;
    }
    
    public boolean requiresIntervention() {
        return this == REVIEW || this == BLOCK;
    }
}