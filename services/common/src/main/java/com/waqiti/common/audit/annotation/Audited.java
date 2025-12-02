package com.waqiti.common.audit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods that should be audited.
 * When applied to a method, all executions will be logged to the audit trail.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    
    /**
     * The action being performed.
     * If not specified, the method name will be used.
     */
    String action() default "";
    
    /**
     * The event type for classification.
     */
    String eventType() default "";
    
    /**
     * The event category for audit classification.
     * Examples: "USER_MANAGEMENT", "FINANCIAL_TRANSACTION", "SECURITY", "DATA_ACCESS"
     */
    String eventCategory() default "GENERAL";
    
    /**
     * The resource type being accessed.
     */
    String resourceType() default "";
    
    /**
     * Whether to include method parameters in the audit log.
     * Be careful with sensitive data - parameters will be masked if they contain PII.
     */
    boolean includeParameters() default true;
    
    /**
     * Whether to include the method's return value in the audit log.
     * Be careful with sensitive data - return values will be masked if they contain PII.
     */
    boolean includeResult() default false;
    
    /**
     * Whether to log the request data.
     */
    boolean logRequest() default true;
    
    /**
     * Whether to log the response data.
     */
    boolean logResponse() default false;
    
    /**
     * Whether to log data access operations.
     */
    boolean logDataAccess() default false;
    
    /**
     * Whether to mask sensitive data in logs.
     */
    boolean maskSensitiveData() default true;
    
    /**
     * Whether to include the payload in the audit log.
     */
    boolean includePayload() default false;
    
    /**
     * Whether this audit event should always be logged, regardless of log level.
     * Use for critical operations that must always be audited.
     */
    boolean alwaysLog() default false;
    
    /**
     * The risk level of this operation for compliance purposes.
     * Values: "LOW", "MEDIUM", "HIGH", "CRITICAL"
     */
    String riskLevel() default "LOW";
    
    /**
     * Whether to capture the full stack trace on failure.
     */
    boolean captureStackTrace() default false;
    
    /**
     * Custom metadata keys to extract from the method context.
     * These will be added to the audit event metadata.
     */
    String[] metadataKeys() default {};
    
    /**
     * Whether this operation involves PII data.
     * If true, additional PII protection measures will be applied.
     */
    boolean involvesPII() default false;
    
    /**
     * The compliance requirements this audit supports.
     * Examples: "SOX", "PCI-DSS", "GDPR", "HIPAA"
     */
    String[] complianceRequirements() default {};
}