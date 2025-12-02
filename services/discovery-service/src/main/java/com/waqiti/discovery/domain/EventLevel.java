package com.waqiti.discovery.domain;

/**
 * Event severity level
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
public enum EventLevel {
    /**
     * Informational event
     */
    INFO,

    /**
     * Warning event - requires monitoring
     */
    WARNING,

    /**
     * Error event - requires attention
     */
    ERROR,

    /**
     * Critical event - requires immediate action
     */
    CRITICAL,

    /**
     * Debug event - for troubleshooting
     */
    DEBUG;

    /**
     * Check if level requires alerting
     *
     * @return true if alerts should be sent
     */
    public boolean requiresAlert() {
        return this == ERROR || this == CRITICAL;
    }
}
