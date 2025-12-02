package com.waqiti.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to enable rate limiting on methods
 * 
 * Usage examples:
 * 
 * // Basic rate limiting by IP address
 * @RateLimited
 * public void someMethod() { }
 * 
 * // Rate limiting by user ID with custom limits
 * @RateLimited(keyType = KeyType.USER, capacity = 50, refillTokens = 50, refillPeriodMinutes = 5)
 * public void userSpecificMethod() { }
 * 
 * // Rate limiting with custom key
 * @RateLimited(keyType = KeyType.CUSTOM, customKeyExpression = "#args[0]")
 * public void methodWithCustomKey(String accountId) { }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {
    
    /**
     * Type of key to use for rate limiting
     */
    KeyType keyType() default KeyType.IP;
    
    /**
     * Number of tokens to consume per request
     */
    int tokens() default 1;
    
    /**
     * Maximum capacity of the bucket
     */
    long capacity() default -1;
    
    /**
     * Number of tokens to refill
     */
    long refillTokens() default -1;
    
    /**
     * Period in minutes for token refill
     */
    long refillPeriodMinutes() default -1;
    
    /**
     * Custom prefix for the rate limit key
     */
    String prefix() default "";
    
    /**
     * Expression to resolve custom key (used when keyType = CUSTOM)
     * Supports simple parameter extraction like #args[0]
     */
    String customKeyExpression() default "";
    
    /**
     * Error message to return when rate limit is exceeded
     */
    String errorMessage() default "Rate limit exceeded. Please try again later.";
    
    /**
     * Key types for rate limiting
     */
    enum KeyType {
        /**
         * Rate limit by IP address
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
         * Rate limit using custom key expression
         */
        CUSTOM,
        
        /**
         * Rate limit by method name (shared across all callers)
         */
        METHOD,
        
        /**
         * Global rate limit (shared by everyone)
         */
        GLOBAL
    }
}