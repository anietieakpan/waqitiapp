package com.waqiti.common.security.model;

/**
 * Rate limit tiers
 */
public enum RateLimitTier {
    UNRESTRICTED(0),
    HIGH(1000),
    STANDARD(100),
    LOW(10),
    RESTRICTED(1);
    
    private final int requestsPerMinute;
    
    RateLimitTier(int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }
    
    public int getRequestsPerMinute() {
        return requestsPerMinute;
    }
}