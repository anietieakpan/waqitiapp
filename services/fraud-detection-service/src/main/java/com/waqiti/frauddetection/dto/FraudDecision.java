package com.waqiti.frauddetection.dto;

/**
 * Fraud detection decision enumeration
 */
public enum FraudDecision {
    APPROVE("approve", "Transaction approved - low fraud risk", 0),
    APPROVE_WITH_MONITORING("approve_with_monitoring", "Transaction approved with enhanced monitoring", 1),
    REVIEW("review", "Transaction requires review - medium risk", 2),
    MANUAL_REVIEW("manual_review", "Transaction requires manual review - high risk", 3),
    CHALLENGE("challenge", "Transaction requires additional authentication", 4),
    STEP_UP_AUTH("step_up_auth", "Transaction requires step-up authentication", 5),
    BLOCK("block", "Transaction blocked - high fraud risk", 6),
    REJECT("reject", "Transaction rejected - critical fraud risk", 7);
    
    private final String code;
    private final String description;
    private final int severity;
    
    FraudDecision(String code, String description, int severity) {
        this.code = code;
        this.description = description;
        this.severity = severity;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    public int getSeverity() {
        return severity;
    }
    
    /**
     * Check if decision allows transaction to proceed
     */
    public boolean allowsTransaction() {
        return this == APPROVE || 
               this == APPROVE_WITH_MONITORING || 
               this == CHALLENGE || 
               this == STEP_UP_AUTH;
    }
    
    /**
     * Check if decision blocks transaction
     */
    public boolean blocksTransaction() {
        return this == BLOCK || this == REJECT;
    }
    
    /**
     * Check if decision requires human review
     */
    public boolean requiresHumanReview() {
        return this == REVIEW || this == MANUAL_REVIEW;
    }
    
    /**
     * Check if decision requires additional authentication
     */
    public boolean requiresAdditionalAuth() {
        return this == CHALLENGE || this == STEP_UP_AUTH;
    }
    
    /**
     * Get decision from risk level
     */
    public static FraudDecision fromRiskLevel(RiskLevel riskLevel, boolean hasStrongAuth) {
        switch (riskLevel) {
            case LOW:
                return APPROVE;
            case MEDIUM:
                return hasStrongAuth ? APPROVE_WITH_MONITORING : CHALLENGE;
            case HIGH:
                return hasStrongAuth ? REVIEW : STEP_UP_AUTH;
            case CRITICAL:
                return BLOCK;
            default:
                return REJECT;
        }
    }
    
    /**
     * Check if this decision is more severe than another
     */
    public boolean isMoreSevereThan(FraudDecision other) {
        return this.severity > other.severity;
    }
}