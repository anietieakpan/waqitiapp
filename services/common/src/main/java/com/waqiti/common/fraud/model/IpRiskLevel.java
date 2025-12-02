package com.waqiti.common.fraud.model;

/**
 * Risk levels for IP address analysis
 */
public enum IpRiskLevel {
    
    /**
     * Safe IP - No risk indicators
     */
    SAFE(0.1, "Safe IP address", "No risk indicators detected"),
    
    /**
     * Low risk - Minor indicators
     */
    LOW(0.3, "Low risk IP", "Minor risk indicators present"),
    
    /**
     * Medium risk - Some concerning patterns
     */
    MEDIUM(0.5, "Medium risk IP", "Some concerning patterns detected"),
    
    /**
     * High risk - Multiple risk factors
     */
    HIGH(0.7, "High risk IP", "Multiple risk factors detected"),
    
    /**
     * Critical risk - Known malicious IP
     */
    CRITICAL(0.9, "Critical risk IP", "Known malicious or compromised IP"),
    
    /**
     * Blocked - IP should be blocked
     */
    BLOCKED(1.0, "Blocked IP", "IP is on blocklist or confirmed malicious");
    
    private final double score;
    private final String displayName;
    private final String description;
    
    IpRiskLevel(double score, String displayName, String description) {
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
     * Check if this risk level requires blocking
     */
    public boolean requiresBlocking() {
        return this.ordinal() >= HIGH.ordinal();
    }
    
    /**
     * Check if this risk level requires additional verification
     */
    public boolean requiresVerification() {
        return this.ordinal() >= MEDIUM.ordinal();
    }
    
    /**
     * Get risk level from numerical score
     */
    public static IpRiskLevel fromScore(double score) {
        if (score >= 1.0) return BLOCKED;
        if (score >= 0.9) return CRITICAL;
        if (score >= 0.7) return HIGH;
        if (score >= 0.5) return MEDIUM;
        if (score >= 0.3) return LOW;
        return SAFE;
    }
    
    /**
     * Check if this IP should be allowed through
     */
    public boolean allowsAccess() {
        return this.ordinal() < HIGH.ordinal();
    }
}