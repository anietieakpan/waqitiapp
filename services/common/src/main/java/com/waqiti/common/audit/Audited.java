package com.waqiti.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods or classes for audit logging
 * 
 * Usage examples:
 * 
 * // Basic audit logging
 * @Audited
 * public void updateUser(User user) { }
 * 
 * // Detailed audit configuration
 * @Audited(
 *     eventType = "USER_MODIFICATION",
 *     resourceType = "USER",
 *     logDataAccess = true,
 *     maskSensitiveData = true
 * )
 * public User getUser(String userId) { }
 * 
 * // Financial transaction audit
 * @Audited(
 *     eventType = "PAYMENT_PROCESSING",
 *     eventCategory = "FINANCIAL",
 *     includePayload = true,
 *     logRequest = true,
 *     logResponse = true
 * )
 * public PaymentResponse processPayment(PaymentRequest request) { }
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    
    /**
     * Type of event being audited
     * Default: Inferred from method name
     */
    String eventType() default "";
    
    /**
     * Category of the event
     * Default: USER_ACTIVITY
     */
    String eventCategory() default "USER_ACTIVITY";
    
    /**
     * Type of resource being accessed/modified
     * Default: Inferred from controller/method
     */
    String resourceType() default "";
    
    /**
     * Action being performed
     * Default: Inferred from HTTP method
     */
    String action() default "";
    
    /**
     * Whether to log the request details
     * Default: false (for performance)
     */
    boolean logRequest() default false;
    
    /**
     * Whether to log the response details
     * Default: false (for performance)
     */
    boolean logResponse() default false;
    
    /**
     * Whether to log data access events
     * Default: true for GET operations on sensitive resources
     */
    boolean logDataAccess() default false;
    
    /**
     * Whether to mask sensitive data in logs
     * Default: true
     */
    boolean maskSensitiveData() default true;
    
    /**
     * Whether to include request/response payload
     * Default: false (for performance and privacy)
     */
    boolean includePayload() default false;
    
    /**
     * Risk level of the operation
     * Default: Determined dynamically
     */
    String riskLevel() default "";
    
    /**
     * Whether this is a compliance-critical operation
     * Default: false
     */
    boolean complianceCritical() default false;
    
    /**
     * Custom tags for the audit event
     */
    String[] tags() default {};
    
    /**
     * Whether to always log (bypasses sampling)
     * Default: true for critical operations
     */
    boolean alwaysLog() default false;
    
    /**
     * Regulatory requirement tags (PCI-DSS, GDPR, SOX, etc.)
     */
    String[] regulations() default {};
    
    /**
     * Whether this operation involves PII data
     * Default: false
     */
    boolean involvesPII() default false;
    
    /**
     * Whether this operation involves card holder data
     * Default: false
     */
    boolean involvesCardData() default false;
}