package com.waqiti.common.fraud.model;

/**
 * Enumeration of fraud risk levels with associated thresholds and actions
 */
public enum FraudRiskLevel {

    UNKNOWN(-1.0, 0.0, "Unable to assess", "Risk level could not be determined"),
    MINIMAL(0.0, 0.1, "Allow transaction", "No risk detected"),
    LOW(0.1, 0.3, "Allow transaction", "Standard processing"),
    MEDIUM(0.3, 0.7, "Additional verification", "Enhanced monitoring"),
    HIGH(0.7, 0.9, "Manual review required", "Block and review"),
    CRITICAL(0.9, 1.0, "Block immediately", "Immediate investigation");
    
    private final double minThreshold;
    private final double maxThreshold;
    private final String recommendedAction;
    private final String description;
    
    FraudRiskLevel(double minThreshold, double maxThreshold, String recommendedAction, String description) {
        this.minThreshold = minThreshold;
        this.maxThreshold = maxThreshold;
        this.recommendedAction = recommendedAction;
        this.description = description;
    }
    
    public double getMinThreshold() {
        return minThreshold;
    }
    
    public double getMaxThreshold() {
        return maxThreshold;
    }
    
    public String getRecommendedAction() {
        return recommendedAction;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Determine risk level based on fraud score
     */
    public static FraudRiskLevel fromScore(double score) {
        if (score >= CRITICAL.minThreshold) return CRITICAL;
        if (score >= HIGH.minThreshold) return HIGH;
        if (score >= MEDIUM.minThreshold) return MEDIUM;
        if (score >= LOW.minThreshold) return LOW;
        return MINIMAL;
    }
    
    /**
     * Check if this risk level requires immediate action
     */
    public boolean requiresImmediateAction() {
        return this == HIGH || this == CRITICAL;
    }
    
    /**
     * Check if this risk level allows automatic processing
     */
    public boolean allowsAutoProcessing() {
        return this == MINIMAL || this == LOW;
    }

    /**
     * PRODUCTION FIX: Convert from DTO package FraudRiskLevel
     */
    public static FraudRiskLevel fromDto(com.waqiti.common.fraud.dto.FraudRiskLevel dtoLevel) {
        if (dtoLevel == null) return UNKNOWN;
        return switch (dtoLevel) {
            case MINIMAL -> MINIMAL;
            case LOW -> LOW;
            case MEDIUM -> MEDIUM;
            case HIGH -> HIGH;
            case CRITICAL -> CRITICAL;
            case UNKNOWN -> UNKNOWN;
        };
    }

    /**
     * PRODUCTION FIX: Convert to DTO package FraudRiskLevel
     */
    public com.waqiti.common.fraud.dto.FraudRiskLevel toDto() {
        return switch (this) {
            case MINIMAL -> com.waqiti.common.fraud.dto.FraudRiskLevel.MINIMAL;
            case LOW -> com.waqiti.common.fraud.dto.FraudRiskLevel.LOW;
            case MEDIUM -> com.waqiti.common.fraud.dto.FraudRiskLevel.MEDIUM;
            case HIGH -> com.waqiti.common.fraud.dto.FraudRiskLevel.HIGH;
            case CRITICAL -> com.waqiti.common.fraud.dto.FraudRiskLevel.CRITICAL;
            case UNKNOWN -> com.waqiti.common.fraud.dto.FraudRiskLevel.UNKNOWN;
        };
    }
}