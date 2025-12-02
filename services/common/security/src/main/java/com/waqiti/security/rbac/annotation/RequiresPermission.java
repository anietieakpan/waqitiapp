package com.waqiti.security.rbac.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for method-level permission checking.
 * Can be used on controller methods or service methods to enforce RBAC.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {
    
    /**
     * Required permissions (user must have at least one)
     */
    String[] value() default {};
    
    /**
     * All required permissions (user must have all)
     */
    String[] allOf() default {};
    
    /**
     * Resource type for dynamic permission checking
     * Examples: WALLET, PAYMENT, TRANSACTION, ACCOUNT, NOTIFICATION, LEDGER
     */
    String resource() default "";
    
    /**
     * SpEL expression to extract resource ID from method parameters
     * If not specified, common parameter names will be auto-detected (id, walletId, paymentId, etc.)
     */
    String resourceId() default "";
    
    /**
     * Whether to check permission for the authenticated user's own resources
     * When true, validates actual resource ownership via database queries
     */
    boolean allowOwner() default false;
    
    /**
     * Whether this operation requires enhanced authentication (MFA, biometric, etc.)
     * Used for high-risk operations like large transfers
     */
    boolean requiresEnhancedAuth() default false;
    
    /**
     * Audit level for this operation
     */
    AuditLevel auditLevel() default AuditLevel.NORMAL;
    
    /**
     * Audit levels for compliance tracking
     */
    enum AuditLevel {
        NONE,       // No audit logging
        NORMAL,     // Standard audit logging
        DETAILED,   // Detailed audit with request/response data
        COMPLIANCE  // Full compliance audit with immutable logs
    }
    
    /**
     * Custom error message when permission is denied
     */
    String message() default "Access denied: Insufficient permissions";
    
    /**
     * Whether to fail silently (return null/empty) instead of throwing exception
     */
    boolean failSilently() default false;
    
    /**
     * Cache the permission check result for this duration (seconds)
     */
    int cacheFor() default 0;
}