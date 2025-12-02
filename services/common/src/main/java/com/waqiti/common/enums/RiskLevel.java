package com.waqiti.common.enums;

/**
 * Common risk level enumeration used across all services
 * Represents standardized risk assessment levels for various business contexts
 */
public enum RiskLevel {
    CRITICAL("Critical", 5, "#dc3545"),
    HIGH("High", 4, "#fd7e14"),
    MEDIUM("Medium", 3, "#ffc107"),
    LOW("Low", 2, "#28a745"),
    MINIMAL("Minimal", 1, "#20c997"),
    UNKNOWN("Unknown", 0, "#6c757d");
    
    private final String displayName;
    private final int priority;
    private final String colorCode;
    
    RiskLevel(String displayName, int priority, String colorCode) {
        this.displayName = displayName;
        this.priority = priority;
        this.colorCode = colorCode;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public int getPriority() {
        return priority;
    }
    
    public String getColorCode() {
        return colorCode;
    }
    
    /**
     * Check if this risk level requires immediate attention
     */
    public boolean requiresImmediateAttention() {
        return this == CRITICAL || this == HIGH;
    }
    
    /**
     * Check if this risk level is acceptable for automated processing
     */
    public boolean isAcceptableForAutomation() {
        return this == LOW || this == MINIMAL;
    }
}