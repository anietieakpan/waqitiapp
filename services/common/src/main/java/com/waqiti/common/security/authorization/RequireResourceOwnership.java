package com.waqiti.common.security.authorization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Security annotation to enforce resource ownership validation on controller methods.
 * 
 * This annotation ensures that authenticated users can only access resources they own
 * or have explicit permissions to access, preventing authorization bypass vulnerabilities.
 * 
 * Example usage:
 * <pre>
 * {@code
 * @RequireResourceOwnership(resourceType = "PAYMENT", operation = "READ")
 * public ResponseEntity<Payment> getPayment(@PathVariable UUID paymentId) {
 *     // Method will only execute if user owns the payment or has explicit permission
 * }
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireResourceOwnership {
    
    /**
     * The type of resource being accessed (e.g., PAYMENT, WALLET, TRANSACTION)
     */
    String resourceType();
    
    /**
     * The operation being performed (e.g., READ, WRITE, DELETE, TRANSFER)
     */
    String operation() default "READ";
    
    /**
     * The name of the method parameter containing the resource ID.
     * If empty, the aspect will try to find it automatically.
     */
    String resourceIdParam() default "";
    
    /**
     * Whether this operation requires enhanced authentication (MFA, biometric, etc.)
     */
    boolean requiresEnhancedAuth() default false;
    
    /**
     * Whether this operation should be audited for compliance
     */
    boolean auditRequired() default true;
    
    /**
     * Custom permission override. If specified, this permission will be checked
     * in addition to resource ownership.
     */
    String customPermission() default "";
    
    /**
     * Whether to allow access if the user has admin privileges
     */
    boolean allowAdminOverride() default true;
}