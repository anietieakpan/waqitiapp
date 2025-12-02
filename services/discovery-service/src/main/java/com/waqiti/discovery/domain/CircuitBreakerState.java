package com.waqiti.discovery.domain;

/**
 * Circuit breaker state
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
public enum CircuitBreakerState {
    /**
     * Circuit is closed - requests flow normally
     */
    CLOSED,

    /**
     * Circuit is open - requests are blocked
     */
    OPEN,

    /**
     * Circuit is half-open - limited requests allowed to test recovery
     */
    HALF_OPEN,

    /**
     * Circuit breaker is disabled
     */
    DISABLED,

    /**
     * Circuit breaker is in forced open state
     */
    FORCED_OPEN;

    /**
     * Check if circuit allows requests
     *
     * @return true if requests are allowed
     */
    public boolean allowsRequests() {
        return this == CLOSED || this == HALF_OPEN;
    }

    /**
     * Check if circuit blocks requests
     *
     * @return true if requests are blocked
     */
    public boolean blocksRequests() {
        return this == OPEN || this == FORCED_OPEN;
    }
}
