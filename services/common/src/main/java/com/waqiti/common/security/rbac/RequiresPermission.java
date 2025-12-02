package com.waqiti.common.security.rbac;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to require specific permission for method execution
 *
 * Usage:
 * <pre>
 * {@code
 * @RequiresPermission(Permission.PAYMENT_WRITE)
 * public PaymentResponse createPayment(PaymentRequest request) {
 *     // Method implementation
 * }
 * }
 * </pre>
 *
 * @author Waqiti Platform Engineering
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {
    /**
     * The permission required to execute this method
     */
    Permission value();

    /**
     * Error message to show when permission is denied
     * Default: "Access denied: insufficient permissions"
     */
    String message() default "Access denied: insufficient permissions";
}
