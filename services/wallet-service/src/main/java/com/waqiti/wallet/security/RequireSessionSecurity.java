/**
 * SECURITY ENHANCEMENT: Session Security Validation Annotation
 * Applied to methods that require session security validation
 */
package com.waqiti.wallet.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods that require session security validation
 * The aspect will automatically validate session security before method execution
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireSessionSecurity {
    
    /**
     * The operation type for security validation
     * Defaults to the method name in uppercase
     */
    String operationType() default "";
    
    /**
     * Whether to require session regeneration if security checks fail
     * Default is true for backward compatibility
     */
    boolean requireRegeneration() default true;
    
    /**
     * Custom reason for security validation
     * Used in audit logs
     */
    String reason() default "";
}