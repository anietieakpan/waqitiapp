package com.waqiti.common.ratelimit.exception;

/**
 * Rate Limit Configuration Exception
 * 
 * Thrown when rate limiting configuration is invalid or misconfigured.
 */
public class RateLimitConfigurationException extends RuntimeException {
    
    public RateLimitConfigurationException(String message) {
        super(message);
    }
    
    public RateLimitConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}