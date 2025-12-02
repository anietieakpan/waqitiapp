package com.waqiti.common.ratelimit;

/**
 * Rate limiting strategies for different use cases
 */
public enum RateLimitStrategy {
    
    /**
     * Rate limit by IP address (default)
     * Good for preventing DDoS and general abuse
     */
    IP,
    
    /**
     * Rate limit by authenticated user ID
     * Good for limiting API usage per user account
     */
    USER,
    
    /**
     * Rate limit by API key
     * Good for third-party integrations
     */
    API_KEY,
    
    /**
     * Rate limit by endpoint globally
     * Good for protecting resource-intensive operations
     */
    ENDPOINT,
    
    /**
     * Global rate limit across all requests
     * Good for overall system protection
     */
    GLOBAL,
    
    /**
     * Combination of IP and User ID
     * Most restrictive, prevents both anonymous and authenticated abuse
     */
    IP_AND_USER
}