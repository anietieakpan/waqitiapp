package com.waqiti.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * PRODUCTION-GRADE Rate Limiting Annotation for Financial APIs
 * 
 * CRITICAL SECURITY FEATURE - Protects against:
 * - DDoS attacks and API abuse
 * - Brute force authentication attempts  
 * - High-frequency trading abuse
 * - Payment fraud through rapid requests
 * - Resource exhaustion attacks
 * 
 * Usage Examples:
 * 
 * // High-Value Payment Endpoints (per user)
 * @RateLimit(requests = 10, window = 1, unit = TimeUnit.MINUTES, keyType = KeyType.USER)
 * 
 * // Authentication Endpoints (IP-based brute force protection)
 * @RateLimit(requests = 5, window = 5, unit = TimeUnit.MINUTES, keyType = KeyType.IP,
 *           burstAllowed = false, blockDuration = 15, blockUnit = TimeUnit.MINUTES)
 * 
 * // Critical Financial Operations
 * @RateLimit(requests = 3, window = 1, unit = TimeUnit.HOURS, keyType = KeyType.USER,
 *           requireMfa = true, priority = Priority.CRITICAL)
 * 
 * // Admin-Only Operations
 * @RateLimit(requests = 5, window = 1, unit = TimeUnit.MINUTES, keyType = KeyType.USER,
 *           userTypes = {"ADMIN"}, exemptUserTypes = {"SUPER_ADMIN"})
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    
    /**
     * Maximum number of requests allowed within the time window
     */
    int requests() default 60;
    
    /**
     * Time window duration
     */
    int window() default 1;
    
    /**
     * Time unit for the window (SECONDS, MINUTES, HOURS)
     */
    TimeUnit unit() default TimeUnit.MINUTES;
    
    /**
     * Duration window in seconds (DEPRECATED - use window + unit instead)
     */
    @Deprecated
    int durationSeconds() default -1;
    
    /**
     * Type of key to use for rate limiting bucketing
     */
    KeyType keyType() default KeyType.IP;
    
    /**
     * Rate limiting strategy (DEPRECATED - use keyType instead)
     */
    @Deprecated
    RateLimitStrategy strategy() default RateLimitStrategy.IP;
    
    /**
     * Allow burst requests above the limit
     */
    boolean burstAllowed() default true;
    
    /**
     * Maximum burst capacity (additional requests allowed)
     */
    int burstCapacity() default 10;
    
    /**
     * Block duration when rate limit is exceeded
     */
    long blockDuration() default 0;
    
    /**
     * Time unit for block duration
     */
    TimeUnit blockUnit() default TimeUnit.MINUTES;
    
    /**
     * User types that this rate limit applies to (empty = all users)
     */
    String[] userTypes() default {};
    
    /**
     * User types that are exempt from this rate limit
     */
    String[] exemptUserTypes() default {};
    
    /**
     * Custom rate limit key expression (SpEL supported)
     */
    String customKey() default "";
    
    /**
     * Require MFA validation for requests at this limit
     */
    boolean requireMfa() default false;
    
    /**
     * Alert when this percentage of limit is reached
     */
    double alertThreshold() default 0.8;
    
    /**
     * Custom error message when rate limit is exceeded
     */
    String errorMessage() default "Rate limit exceeded. Please try again later.";
    
    /**
     * Whether to include this endpoint in global rate limiting
     */
    boolean includeInGlobal() default true;
    
    /**
     * Skip rate limiting entirely (for emergency overrides)
     */
    boolean skipRateLimit() default false;
    
    /**
     * Endpoint description for monitoring and alerting
     */
    String description() default "";
    
    /**
     * Priority level for rate limiting (higher priority = more strict)
     */
    Priority priority() default Priority.MEDIUM;
    
    /**
     * Enable distributed rate limiting across multiple service instances
     */
    boolean distributed() default true;
    
    /**
     * Rate limit scope for multi-tenant environments
     */
    Scope scope() default Scope.GLOBAL;
    
    /**
     * Key types for rate limiting
     */
    enum KeyType {
        /**
         * Rate limit by client IP address
         */
        IP,
        
        /**
         * Rate limit by authenticated user ID
         */
        USER,
        
        /**
         * Rate limit by API key
         */
        API_KEY,
        
        /**
         * Rate limit by combination of user + IP
         */
        USER_IP,
        
        /**
         * Rate limit by method/endpoint globally
         */
        ENDPOINT,
        
        /**
         * Rate limit using custom key expression
         */
        CUSTOM,
        
        /**
         * Rate limit by tenant organization
         */
        TENANT,
        
        /**
         * Rate limit by device ID
         */
        DEVICE,
        
        /**
         * Rate limit by session ID
         */
        SESSION
    }
    
    /**
     * Priority levels for rate limiting
     */
    enum Priority {
        LOW(1),
        MEDIUM(2), 
        HIGH(3),
        CRITICAL(4),
        EMERGENCY(5);
        
        private final int level;
        
        Priority(int level) {
            this.level = level;
        }
        
        public int getLevel() {
            return level;
        }
    }
    
    /**
     * Rate limiting scope
     */
    enum Scope {
        /**
         * Global rate limiting across all tenants
         */
        GLOBAL,
        
        /**
         * Rate limiting per tenant
         */
        TENANT,
        
        /**
         * Rate limiting per service instance
         */
        INSTANCE
    }
}