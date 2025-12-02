package com.waqiti.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Security annotation to validate resource ownership before allowing access.
 * Used for fraud detection and compliance systems to ensure users can only
 * access their own resources and data.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidateOwnership {
    
    /**
     * Resource type being accessed
     */
    ResourceType resourceType();
    
    /**
     * Parameter name containing the resource identifier
     */
    String resourceIdParam() default "id";
    
    /**
     * Parameter name containing the user identifier
     */
    String userIdParam() default "userId";
    
    /**
     * Whether to allow admin override
     */
    boolean allowAdminOverride() default true;
    
    /**
     * Required permission level
     */
    PermissionLevel requiredPermission() default PermissionLevel.READ;
    
    /**
     * Custom validation method name
     */
    String customValidator() default "";
    
    /**
     * Error message to return on validation failure
     */
    String errorMessage() default "Access denied: insufficient permissions";
    
    /**
     * Whether to audit access attempts
     */
    boolean auditAccess() default true;
    
    /**
     * Operation being performed
     */
    String operation() default "";
    
    /**
     * Permission levels
     */
    enum PermissionLevel {
        READ, WRITE, DELETE, ADMIN
    }
}