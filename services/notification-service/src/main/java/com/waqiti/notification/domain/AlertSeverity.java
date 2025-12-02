package com.waqiti.notification.domain;

/**
 * Alert severity levels
 */
public enum AlertSeverity {
    /**
     * Critical severity - requires immediate attention
     */
    CRITICAL("CRITICAL", 1),

    /**
     * High severity - requires urgent attention
     */
    HIGH("HIGH", 2),

    /**
     * Medium severity - requires attention within reasonable timeframe
     */
    MEDIUM("MEDIUM", 3),

    /**
     * Low severity - informational or minor issues
     */
    LOW("LOW", 4),

    /**
     * Info severity - purely informational
     */
    INFO("INFO", 5);

    private final String level;
    private final int priority;

    AlertSeverity(String level, int priority) {
        this.level = level;
        this.priority = priority;
    }

    public String getLevel() {
        return level;
    }

    public int getPriority() {
        return priority;
    }

    /**
     * Check if this severity is higher than another
     */
    public boolean isHigherThan(AlertSeverity other) {
        return this.priority < other.priority;
    }

    /**
     * Check if this is a critical or high severity alert
     */
    public boolean isCriticalOrHigh() {
        return this == CRITICAL || this == HIGH;
    }

    /**
     * Get severity from string value
     */
    public static AlertSeverity fromString(String level) {
        for (AlertSeverity severity : values()) {
            if (severity.level.equalsIgnoreCase(level)) {
                return severity;
            }
        }
        throw new IllegalArgumentException("Unknown alert severity: " + level);
    }
}