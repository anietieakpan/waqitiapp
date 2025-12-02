package com.waqiti.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for method-level rate limiting
 * 
 * @author Waqiti Security Team
 * @since 2.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {
    
    /**
     * Number of requests allowed per time window
     */
    int requestsPerMinute() default 60;
    
    /**
     * Rate limit scope - per user, per IP, or global
     */
    RateLimitScope scope() default RateLimitScope.USER;
    
    /**
     * Custom rate limit key expression (SpEL)
     */
    String key() default "";
    
    /**
     * Custom error message when rate limit is exceeded
     */
    String message() default "Rate limit exceeded";
    
    /**
     * Whether to include IP in rate limit key for user scope
     */
    boolean includeIp() default false;
    
    /**
     * Burst allowance multiplier
     */
    double burstMultiplier() default 1.5;
    
    enum RateLimitScope {
        USER,      // Per authenticated user
        IP,        // Per IP address
        API_KEY,   // Per API key
        GLOBAL,    // Global across all requests
        CUSTOM     // Use custom key expression
    }
}