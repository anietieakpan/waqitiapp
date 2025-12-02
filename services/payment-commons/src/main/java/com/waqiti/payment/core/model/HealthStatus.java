package com.waqiti.payment.core.model;

/**
 * Health status enumeration
 */
public enum HealthStatus {
    UP("Service is fully operational"),
    DOWN("Service is not operational"),
    DEGRADED("Service is partially operational"),
    UNKNOWN("Service status is unknown");
    
    private final String description;
    
    HealthStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isOperational() {
        return this == UP || this == DEGRADED;
    }
}