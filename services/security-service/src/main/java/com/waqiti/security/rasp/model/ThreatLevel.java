package com.waqiti.security.rasp.model;

/**
 * Threat level enumeration for security events
 */
public enum ThreatLevel {
    LOW(1, "Low risk - monitoring only"),
    MEDIUM(2, "Medium risk - log and alert"),
    HIGH(3, "High risk - block and alert"),
    CRITICAL(4, "Critical risk - block, alert, and escalate");
    
    private final int priority;
    private final String description;
    
    ThreatLevel(int priority, String description) {
        this.priority = priority;
        this.description = description;
    }
    
    public int getPriority() {
        return priority;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isHigherThan(ThreatLevel other) {
        return this.priority > other.priority;
    }
    
    public boolean shouldBlock() {
        return this == HIGH || this == CRITICAL;
    }
    
    public boolean shouldAlert() {
        return this == MEDIUM || this == HIGH || this == CRITICAL;
    }
}