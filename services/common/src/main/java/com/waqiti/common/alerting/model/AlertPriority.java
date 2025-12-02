package com.waqiti.common.alerting.model;

/**
 * Alert priority for routing and escalation
 */
public enum AlertPriority {

    /**
     * Highest priority - immediate page
     */
    CRITICAL(1),

    /**
     * High priority - page within 5 minutes
     */
    HIGH(2),

    /**
     * Medium priority - notification only
     */
    MEDIUM(3),

    /**
     * Low priority - best effort
     */
    LOW(4);

    private final int level;

    AlertPriority(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public boolean isHigherThan(AlertPriority other) {
        return this.level < other.level;
    }
}
