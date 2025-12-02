package com.waqiti.common.fraud.model;

/**
 * Risk levels for pattern analysis in fraud detection
 */
public enum PatternRiskLevel {
    
    /**
     * Normal pattern - No suspicious behavior detected
     */
    NORMAL(0.1, "Normal pattern", "Standard behavioral patterns detected"),
    
    /**
     * Low risk pattern - Minor anomalies
     */
    LOW(0.3, "Low risk pattern", "Minor anomalies in behavioral patterns"),
    
    /**
     * Medium risk pattern - Notable deviations
     */
    MEDIUM(0.5, "Medium risk pattern", "Notable deviations from normal patterns"),
    
    /**
     * High risk pattern - Strong fraud indicators
     */
    HIGH(0.7, "High risk pattern", "Strong fraud indicators in behavioral patterns"),
    
    /**
     * Critical risk pattern - Confirmed fraud patterns
     */
    CRITICAL(0.9, "Critical risk pattern", "Confirmed fraudulent behavioral patterns"),
    
    /**
     * Malicious pattern - Known attack patterns
     */
    MALICIOUS(1.0, "Malicious pattern", "Known malicious attack patterns detected");
    
    private final double score;
    private final String displayName;
    private final String description;
    
    PatternRiskLevel(double score, String displayName, String description) {
        this.score = score;
        this.displayName = displayName;
        this.description = description;
    }
    
    /**
     * Get the numerical risk score
     */
    public double getScore() {
        return score;
    }
    
    /**
     * Get the display name
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Get the description
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Check if this risk level requires immediate action
     */
    public boolean requiresImmediateAction() {
        return this.ordinal() >= HIGH.ordinal();
    }
    
    /**
     * Check if this risk level requires enhanced monitoring
     */
    public boolean requiresEnhancedMonitoring() {
        return this.ordinal() >= MEDIUM.ordinal();
    }
    
    /**
     * Check if this pattern indicates automated/bot behavior
     */
    public boolean indicatesAutomatedBehavior() {
        return this == HIGH || this == CRITICAL || this == MALICIOUS;
    }
    
    /**
     * Get risk level from numerical score
     */
    public static PatternRiskLevel fromScore(double score) {
        if (score >= 1.0) return MALICIOUS;
        if (score >= 0.9) return CRITICAL;
        if (score >= 0.7) return HIGH;
        if (score >= 0.5) return MEDIUM;
        if (score >= 0.3) return LOW;
        return NORMAL;
    }
    
    /**
     * Check if this pattern allows normal processing
     */
    public boolean allowsNormalProcessing() {
        return this.ordinal() < HIGH.ordinal();
    }
    
    /**
     * Get the severity level for alerting
     */
    public String getSeverity() {
        switch (this) {
            case NORMAL:
            case LOW:
                return "INFO";
            case MEDIUM:
                return "WARNING";
            case HIGH:
                return "ERROR";
            case CRITICAL:
            case MALICIOUS:
                return "CRITICAL";
            default:
                return "INFO";
        }
    }

    /**
     * PRODUCTION FIX: Convert PatternRiskLevel to FraudRiskLevel
     * Maps pattern risk levels to corresponding fraud risk levels
     */
    public FraudRiskLevel toFraudRiskLevel() {
        switch (this) {
            case NORMAL:
                return FraudRiskLevel.MINIMAL;
            case LOW:
                return FraudRiskLevel.LOW;
            case MEDIUM:
                return FraudRiskLevel.MEDIUM;
            case HIGH:
                return FraudRiskLevel.HIGH;
            case CRITICAL:
            case MALICIOUS:
                return FraudRiskLevel.CRITICAL;
            default:
                return FraudRiskLevel.UNKNOWN;
        }
    }
}