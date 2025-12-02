package com.waqiti.common.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Interface for user identity and rate limiting information.
 * 
 * This interface follows the Interface Segregation Principle (ISP) by exposing
 * only the user data needed by cross-module components like rate limiting,
 * audit logging, and security filters.
 * 
 * Benefits:
 * - Avoids circular dependencies between modules
 * - Provides a stable contract for cross-module communication
 * - Allows different implementations in different contexts
 * - Follows dependency inversion principle
 */
public interface UserIdentity {
    
    /**
     * @return Unique user identifier
     */
    UUID getId();
    
    /**
     * @return Username for logging and audit purposes
     */
    String getUsername();
    
    /**
     * @return Email address for notification purposes
     */
    String getEmail();
    
    /**
     * @return User role for authorization checks
     */
    String getRole();
    
    /**
     * @return Whether the user account is active
     */
    boolean isActive();
    
    /**
     * @return Last login time for security monitoring
     */
    LocalDateTime getLastLoginAt();
    
    // Rate limiting specific methods
    
    /**
     * @return Rate limit tier (e.g., "BASIC", "PREMIUM", "ENTERPRISE")
     */
    String getRateLimitTier();
    
    /**
     * @return Daily request limit for this user, null for unlimited
     */
    Integer getDailyRequestLimit();
    
    /**
     * @return Whether this user is exempt from rate limiting
     */
    boolean isRateLimitExempt();
    
    /**
     * @return Custom rate limiting rules for this user
     */
    default java.util.Map<String, Object> getCustomRateLimitRules() {
        return java.util.Collections.emptyMap();
    }
}