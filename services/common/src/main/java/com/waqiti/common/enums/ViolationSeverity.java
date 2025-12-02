package com.waqiti.common.enums;

/**
 * Common violation severity enumeration used across compliance and security modules
 * Represents standardized severity levels for various types of violations
 */
public enum ViolationSeverity {
    CRITICAL("Critical", 4, "#dc3545"),
    HIGH("High", 3, "#fd7e14"),
    MEDIUM("Medium", 2, "#ffc107"),
    LOW("Low", 1, "#28a745");
    
    private final String displayName;
    private final int priority;
    private final String colorCode;
    
    ViolationSeverity(String displayName, int priority, String colorCode) {
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
     * Check if severity requires immediate escalation
     */
    public boolean requiresEscalation() {
        return this == CRITICAL || this == HIGH;
    }
}