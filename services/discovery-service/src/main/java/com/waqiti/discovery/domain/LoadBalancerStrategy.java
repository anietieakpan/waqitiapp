package com.waqiti.discovery.domain;

/**
 * Load balancer strategy for distributing requests across service instances
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
public enum LoadBalancerStrategy {
    /**
     * Round-robin distribution
     */
    ROUND_ROBIN,

    /**
     * Random selection
     */
    RANDOM,

    /**
     * Weighted round-robin based on instance capacity
     */
    WEIGHTED_ROUND_ROBIN,

    /**
     * Least connections - route to instance with fewest active connections
     */
    LEAST_CONNECTIONS,

    /**
     * Least response time - route to fastest responding instance
     */
    LEAST_RESPONSE_TIME,

    /**
     * IP hash - consistent routing based on client IP
     */
    IP_HASH,

    /**
     * Sticky session - maintain session affinity
     */
    STICKY_SESSION,

    /**
     * Weighted response time - combines weight and response time
     */
    WEIGHTED_RESPONSE_TIME,

    /**
     * Zone-aware - prefer instances in same availability zone
     */
    ZONE_AWARE;

    /**
     * Check if strategy requires session tracking
     *
     * @return true if strategy requires session tracking
     */
    public boolean requiresSessionTracking() {
        return this == STICKY_SESSION || this == IP_HASH;
    }

    /**
     * Check if strategy requires metrics
     *
     * @return true if strategy requires metrics
     */
    public boolean requiresMetrics() {
        return this == LEAST_CONNECTIONS || this == LEAST_RESPONSE_TIME
            || this == WEIGHTED_RESPONSE_TIME;
    }
}
