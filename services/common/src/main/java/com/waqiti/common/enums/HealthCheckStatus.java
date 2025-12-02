package com.waqiti.common.enums;

/**
 * Common health check status enumeration used across health monitoring
 * Represents standardized health check results
 */
public enum HealthCheckStatus {
    HEALTHY("Healthy", "#28a745"),
    WARNING("Warning", "#ffc107"), 
    UNHEALTHY("Unhealthy", "#dc3545"),
    TIMEOUT("Timeout", "#fd7e14"),
    UNKNOWN("Unknown", "#6c757d");
    
    private final String displayName;
    private final String colorCode;
    
    HealthCheckStatus(String displayName, String colorCode) {
        this.displayName = displayName;
        this.colorCode = colorCode;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getColorCode() {
        return colorCode;
    }
    
    /**
     * Check if status indicates system is operational
     */
    public boolean isOperational() {
        return this == HEALTHY || this == WARNING;
    }
    
    /**
     * Check if status requires immediate attention
     */
    public boolean requiresAttention() {
        return this == UNHEALTHY || this == TIMEOUT;
    }
}