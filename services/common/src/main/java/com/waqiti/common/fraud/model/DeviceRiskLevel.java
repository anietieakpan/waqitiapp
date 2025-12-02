package com.waqiti.common.fraud.model;

/**
 * Risk level classification for device-based fraud assessment
 */
public enum DeviceRiskLevel {
    
    TRUSTED(0.0, 0.2, "Known and verified device"),
    NORMAL(0.2, 0.4, "Standard device with typical usage patterns"),
    SUSPICIOUS(0.4, 0.6, "Device showing suspicious characteristics"),
    MEDIUM(0.4, 0.6, "Device showing suspicious characteristics"),  // Alias for SUSPICIOUS
    UNTRUSTED(0.6, 0.8, "Device with multiple risk indicators"),
    BLACKLISTED(0.8, 1.0, "Device identified as compromised or fraudulent");
    
    private final double minScore;
    private final double maxScore;
    private final String description;
    
    DeviceRiskLevel(double minScore, double maxScore, String description) {
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
     * Determine device risk level based on score
     */
    public static DeviceRiskLevel fromScore(double score) {
        if (score >= BLACKLISTED.minScore) return BLACKLISTED;
        if (score >= UNTRUSTED.minScore) return UNTRUSTED;
        if (score >= SUSPICIOUS.minScore) return SUSPICIOUS;
        if (score >= NORMAL.minScore) return NORMAL;
        return TRUSTED;
    }
    
    /**
     * Check if device requires additional authentication
     */
    public boolean requiresAdditionalAuth() {
        return this == SUSPICIOUS || this == UNTRUSTED || this == BLACKLISTED;
    }
    
    /**
     * Check if device should be blocked
     */
    public boolean shouldBlock() {
        return this == BLACKLISTED;
    }
    
    /**
     * Check if device is trusted
     */
    public boolean isTrusted() {
        return this == TRUSTED;
    }
}