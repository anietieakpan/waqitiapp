package com.waqiti.common.fraud.model;

/**
 * Risk levels for email address analysis
 */
public enum EmailRiskLevel {
    
    /**
     * Safe email - Legitimate domain and patterns
     */
    SAFE(0.1, "Safe email", "Legitimate email address with good reputation"),
    
    /**
     * Low risk - Minor indicators
     */
    LOW(0.3, "Low risk email", "Minor risk indicators present"),
    
    /**
     * Medium risk - Suspicious patterns
     */
    MEDIUM(0.5, "Medium risk email", "Suspicious patterns or domain characteristics"),
    
    /**
     * High risk - Multiple risk factors
     */
    HIGH(0.7, "High risk email", "Multiple risk factors detected"),
    
    /**
     * Critical risk - Known malicious patterns
     */
    CRITICAL(0.9, "Critical risk email", "Known malicious patterns or compromised email"),
    
    /**
     * Blocked - Email should be blocked
     */
    BLOCKED(1.0, "Blocked email", "Email is on blocklist or confirmed fraudulent");
    
    private final double score;
    private final String displayName;
    private final String description;
    
    EmailRiskLevel(double score, String displayName, String description) {
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
     * Check if this risk level requires verification
     */
    public boolean requiresVerification() {
        return this.ordinal() >= MEDIUM.ordinal();
    }
    
    /**
     * Check if this is a disposable email risk level
     */
    public boolean isDisposableEmail() {
        return this == MEDIUM || this == HIGH;
    }
    
    /**
     * Get risk level from numerical score
     */
    public static EmailRiskLevel fromScore(double score) {
        if (score >= 1.0) return BLOCKED;
        if (score >= 0.9) return CRITICAL;
        if (score >= 0.7) return HIGH;
        if (score >= 0.5) return MEDIUM;
        if (score >= 0.3) return LOW;
        return SAFE;
    }
    
    /**
     * Check if this email should be allowed
     */
    public boolean allowsAccess() {
        return this.ordinal() < HIGH.ordinal();
    }
    
    /**
     * Get color code for UI display
     */
    public String getColorCode() {
        switch (this) {
            case SAFE: return "#28a745";     // Green
            case LOW: return "#ffc107";      // Yellow  
            case MEDIUM: return "#fd7e14";   // Orange
            case HIGH: return "#dc3545";     // Red
            case CRITICAL: return "#721c24"; // Dark Red
            case BLOCKED: return "#000000";  // Black
            default: return "#6c757d";       // Gray
        }
    }
}