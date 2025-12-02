package com.waqiti.common.messaging.analysis;

/**
 * Severity levels for failure patterns
 */
public enum FailureSeverity {
    
    /**
     * Informational - no immediate action required
     */
    INFO,
    
    /**
     * Low severity - monitor but not urgent
     */
    LOW,
    
    /**
     * Medium severity - investigate when possible
     */
    MEDIUM,
    
    /**
     * High severity - requires prompt attention
     */
    HIGH,
    
    /**
     * Critical severity - immediate action required
     */
    CRITICAL;
    
    /**
     * Check if this severity requires immediate attention
     */
    public boolean requiresImmediateAction() {
        return this == HIGH || this == CRITICAL;
    }
    
    /**
     * Get numeric priority (higher number = higher priority)
     */
    public int getPriority() {
        return switch (this) {
            case INFO -> 1;
            case LOW -> 2;
            case MEDIUM -> 3;
            case HIGH -> 4;
            case CRITICAL -> 5;
        };
    }
}