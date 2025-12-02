package com.waqiti.common.fraud.model;

/**
 * Risk level classification for account-based fraud assessment
 */
public enum AccountRiskLevel {
    
    SAFE(0.0, 0.2, "Verified account with good history"),
    LOW(0.2, 0.4, "Standard account with minimal risk indicators"),
    MODERATE(0.4, 0.6, "Account with some suspicious activity"),
    MEDIUM(0.4, 0.6, "Account with some suspicious activity"),  // Alias for MODERATE
    ELEVATED(0.6, 0.8, "Account requiring enhanced monitoring"),
    HIGH(0.8, 1.0, "High-risk account requiring immediate review");
    
    private final double minScore;
    private final double maxScore;
    private final String description;
    
    AccountRiskLevel(double minScore, double maxScore, String description) {
        this.minScore = minScore;
        this.maxScore = maxScore;
        this.description = description;
    }
    
    public double getMinScore() {
        return minScore;
    }
    
    public double getMaxScore() {
        return maxScore;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Determine account risk level based on score
     */
    public static AccountRiskLevel fromScore(double score) {
        if (score >= HIGH.minScore) return HIGH;
        if (score >= ELEVATED.minScore) return ELEVATED;
        if (score >= MODERATE.minScore) return MODERATE;
        if (score >= LOW.minScore) return LOW;
        return SAFE;
    }
    
    /**
     * Check if account requires additional verification
     */
    public boolean requiresVerification() {
        return this == ELEVATED || this == HIGH;
    }
    
    /**
     * Check if account should be blocked
     */
    public boolean shouldBlock() {
        return this == HIGH;
    }
}