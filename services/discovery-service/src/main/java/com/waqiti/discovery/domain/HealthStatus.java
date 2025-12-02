package com.waqiti.discovery.domain;

/**
 * Health status enum for service health monitoring
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
public enum HealthStatus {
    /**
     * Service is healthy and operational
     */
    HEALTHY,

    /**
     * Service is degraded but still operational
     */
    DEGRADED,

    /**
     * Service is unhealthy and may not be operational
     */
    UNHEALTHY,

    /**
     * Service health status is unknown
     */
    UNKNOWN,

    /**
     * Service is starting up
     */
    STARTING,

    /**
     * Service is shutting down
     */
    SHUTTING_DOWN;

    /**
     * Check if status is considered healthy
     *
     * @return true if status is HEALTHY or DEGRADED
     */
    public boolean isHealthy() {
        return this == HEALTHY || this == DEGRADED;
    }

    /**
     * Check if status requires attention
     *
     * @return true if status is UNHEALTHY, UNKNOWN, or SHUTTING_DOWN
     */
    public boolean requiresAttention() {
        return this == UNHEALTHY || this == UNKNOWN || this == SHUTTING_DOWN;
    }
}
