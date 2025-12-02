package com.waqiti.discovery.domain;

/**
 * Service registration status
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
public enum ServiceStatus {
    /**
     * Service is up and running
     */
    UP,

    /**
     * Service is down
     */
    DOWN,

    /**
     * Service is starting
     */
    STARTING,

    /**
     * Service is out of service (intentionally taken offline)
     */
    OUT_OF_SERVICE,

    /**
     * Service status is unknown
     */
    UNKNOWN;

    /**
     * Check if service is available for traffic
     *
     * @return true if service can receive traffic
     */
    public boolean isAvailable() {
        return this == UP;
    }
}
