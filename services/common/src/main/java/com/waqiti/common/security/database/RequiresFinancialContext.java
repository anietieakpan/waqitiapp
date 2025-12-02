package com.waqiti.common.security.database;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * CRITICAL SECURITY: Annotation for methods requiring enhanced financial security context
 * 
 * Methods annotated with @RequiresFinancialContext will automatically have enhanced
 * database security context set, including financial transaction tracking and
 * additional audit logging.
 * 
 * Use this annotation on methods that handle:
 * - Money transfers
 * - Wallet operations
 * - Payment processing
 * - Transaction creation/modification
 * - Balance updates
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresFinancialContext {
    
    /**
     * Type of financial operation (for audit purposes)
     */
    String operationType() default "";
    
    /**
     * Whether this operation requires additional compliance logging
     */
    boolean complianceLogging() default true;
    
    /**
     * Minimum required role for this financial operation
     */
    String minimumRole() default "USER";
}