package com.waqiti.common.security;

import java.lang.annotation.*;

/**
 * Annotation to specify required OAuth2 scopes for accessing an endpoint
 * Integrates with Spring Security OAuth2 for fine-grained access control
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresScope {
    
    /**
     * The OAuth2 scope required to access the annotated method or class
     * 
     * @return the required scope name
     */
    String value();
    
    /**
     * Alternative scopes that can satisfy the requirement (OR logic)
     * If specified, any one of these scopes will grant access
     * 
     * @return array of alternative scope names
     */
    String[] alternatives() default {};
    
    /**
     * Additional scopes that must also be present (AND logic)
     * These scopes must be present along with the primary scope
     * 
     * @return array of additional required scope names
     */
    String[] additional() default {};
    
    /**
     * Whether to inherit scope requirements from parent class/method
     * When true, the annotation will combine with parent scope requirements
     * 
     * @return true to inherit parent scope requirements
     */
    boolean inherit() default false;
    
    /**
     * Error message to return when scope requirement is not met
     * If not specified, a default message will be used
     * 
     * @return custom error message
     */
    String message() default "";
    
    /**
     * The resource server that should validate this scope
     * Useful in multi-resource-server environments
     * 
     * @return resource server identifier
     */
    String resourceServer() default "";
    
    /**
     * Whether to validate scope case-sensitively
     * 
     * @return true for case-sensitive validation (default)
     */
    boolean caseSensitive() default true;
    
    /**
     * Scope validation mode
     * 
     * @return the validation mode to use
     */
    ValidationMode mode() default ValidationMode.STRICT;
    
    /**
     * Whether to log scope validation attempts
     * Useful for debugging and auditing
     * 
     * @return true to enable audit logging
     */
    boolean auditLog() default false;
    
    /**
     * Time-based scope restrictions
     * Allows scopes to be valid only during certain time periods
     * 
     * @return time restrictions configuration
     */
    TimeRestriction[] timeRestrictions() default {};
    
    /**
     * Geographic restrictions for scope usage
     * Allows scopes to be restricted to certain regions/countries
     * 
     * @return geographic restrictions
     */
    String[] allowedCountries() default {};
    
    /**
     * IP address restrictions
     * CIDR notation supported (e.g., "192.168.1.0/24")
     * 
     * @return allowed IP addresses or ranges
     */
    String[] allowedIpRanges() default {};
    
    /**
     * Validation modes for scope checking
     */
    enum ValidationMode {
        /**
         * Strict validation - all requirements must be met exactly
         */
        STRICT,
        
        /**
         * Lenient validation - allows partial matches or degraded access
         */
        LENIENT,
        
        /**
         * Hierarchical validation - supports scope hierarchies (e.g., admin implies user)
         */
        HIERARCHICAL,
        
        /**
         * Pattern-based validation - supports wildcard and regex patterns
         */
        PATTERN
    }
    
    /**
     * Time-based restrictions for scope usage
     */
    @Target({})
    @Retention(RetentionPolicy.RUNTIME)
    @interface TimeRestriction {
        /**
         * Start time in HH:mm format (24-hour)
         */
        String startTime();
        
        /**
         * End time in HH:mm format (24-hour)
         */
        String endTime();
        
        /**
         * Days of week when restriction applies
         * 1 = Monday, 7 = Sunday
         */
        int[] daysOfWeek() default {1, 2, 3, 4, 5, 6, 7};
        
        /**
         * Timezone for time calculations
         */
        String timezone() default "UTC";
        
        /**
         * Whether to invert the restriction (allow only outside the time window)
         */
        boolean invert() default false;
    }
}