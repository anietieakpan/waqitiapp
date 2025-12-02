package com.waqiti.common.security.database;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * CRITICAL SECURITY: Annotation for methods requiring admin-level database context
 * 
 * Methods annotated with @RequiresAdminContext will automatically have elevated
 * database security context set with admin privileges, bypassing RLS restrictions
 * where appropriate.
 * 
 * Use this annotation on methods that require:
 * - Cross-user data access
 * - Administrative reports
 * - System maintenance operations
 * - Compliance auditing
 * - Emergency operations
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresAdminContext {
    
    /**
     * Required admin role level
     */
    String requiredRole() default "ADMIN";
    
    /**
     * Reason for requiring admin context (for audit trail)
     */
    String reason() default "";
    
    /**
     * Whether to log all data access in this context
     */
    boolean auditDataAccess() default true;
}