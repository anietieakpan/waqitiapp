package com.waqiti.common.audit.annotation;

import com.waqiti.common.audit.domain.AuditLog.EventCategory;
import com.waqiti.common.audit.domain.AuditLog.Severity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for automatic audit logging using AOP
 * 
 * Apply this annotation to methods that require audit logging for compliance,
 * security monitoring, or forensic investigation purposes.
 * 
 * USAGE EXAMPLES:
 * 
 * Financial Operations:
 * @AuditLogged(
 *     eventType = "PAYMENT_PROCESSED",
 *     category = EventCategory.FINANCIAL,
 *     severity = Severity.HIGH,
 *     pciRelevant = true,
 *     soxRelevant = true
 * )
 * public PaymentResult processPayment(PaymentRequest request) { ... }
 * 
 * Security Operations:
 * @AuditLogged(
 *     eventType = "USER_LOGIN",
 *     category = EventCategory.SECURITY,
 *     severity = Severity.MEDIUM,
 *     description = "User authentication attempt"
 * )
 * public AuthResult authenticate(String username, String password) { ... }
 * 
 * Data Access:
 * @AuditLogged(
 *     eventType = "PII_ACCESSED",
 *     category = EventCategory.DATA_ACCESS,
 *     severity = Severity.HIGH,
 *     gdprRelevant = true,
 *     description = "Personal data accessed"
 * )
 * public UserProfile getUserProfile(String userId) { ... }
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLogged {
    
    /**
     * Event type identifier for the audit log
     * Should be descriptive and follow naming conventions (e.g., "PAYMENT_CREATED", "USER_LOGIN")
     */
    String eventType();
    
    /**
     * Event category for compliance classification
     * Determines which compliance frameworks apply to this event
     */
    EventCategory category();
    
    /**
     * Severity level of the event
     * Used for alerting and prioritization
     */
    Severity severity() default Severity.INFO;
    
    /**
     * Human-readable description of the action being audited
     * Can include placeholders for method parameters: #{param1}, #{param2}
     */
    String description() default "";
    
    /**
     * Entity type being acted upon (e.g., "User", "Payment", "Wallet")
     * Used for resource-based audit trails
     */
    String entityType() default "";
    
    /**
     * SpEL expression to extract entity ID from method parameters
     * Examples: "#request.userId", "#result.paymentId", "#p0.walletId"
     */
    String entityIdExpression() default "";
    
    /**
     * Additional metadata to capture in the audit log
     * Use SpEL expressions to extract data: {"amount": "#request.amount", "currency": "#request.currency"}
     */
    String[] metadata() default {};
    
    // Compliance flags
    
    /**
     * Indicates this event is relevant for PCI DSS compliance
     * Set to true for events involving cardholder data, authentication, or system access
     */
    boolean pciRelevant() default false;
    
    /**
     * Indicates this event is relevant for GDPR compliance
     * Set to true for events involving personal data processing, consent, or data subject rights
     */
    boolean gdprRelevant() default false;
    
    /**
     * Indicates this event is relevant for SOX compliance
     * Set to true for financial transactions, configuration changes, or access controls
     */
    boolean soxRelevant() default false;
    
    /**
     * Indicates this event is relevant for SOC 2 compliance
     * Set to true for security, availability, and operational events
     */
    boolean soc2Relevant() default false;
    
    /**
     * Indicates this event requires immediate notification to compliance team
     * Set to true for critical security events or compliance violations
     */
    boolean requiresNotification() default false;
    
    /**
     * Indicates this event may require investigation
     * Set to true for suspicious activities or security incidents
     */
    boolean investigationRequired() default false;
    
    /**
     * Custom retention policy for this type of event
     * Overrides default retention based on compliance flags
     * Examples: "7_YEARS", "1_YEAR", "90_DAYS"
     */
    String retentionPolicy() default "";
    
    /**
     * Risk score for this type of event (0-100)
     * Used for fraud detection and risk assessment
     */
    int riskScore() default 0;
    
    /**
     * Whether to audit only successful operations
     * Set to false to audit both success and failure
     */
    boolean auditSuccessOnly() default false;
    
    /**
     * Whether to capture method parameters in audit log
     * Parameters are automatically sanitized to remove sensitive data
     */
    boolean captureParameters() default true;
    
    /**
     * Whether to capture return value in audit log
     * Return values are automatically sanitized to remove sensitive data
     */
    boolean captureReturnValue() default false;
    
    /**
     * Whether to capture execution time
     * Useful for performance monitoring and anomaly detection
     */
    boolean captureExecutionTime() default false;
    
    /**
     * Whether to include stack trace in case of exceptions
     * Useful for debugging and security investigation
     */
    boolean includeStackTrace() default false;
    
    /**
     * Custom fields to exclude from parameter/return value capture
     * Sensitive fields that should never be logged
     */
    String[] excludeFields() default {"password", "pin", "cvv", "ssn", "cardNumber", "accountNumber"};
    
    /**
     * Whether this audit event should be sent to external SIEM systems
     * Set to true for security-critical events
     */
    boolean sendToSiem() default false;
    
    /**
     * Whether to perform asynchronous audit logging
     * Set to false for critical operations that must ensure audit completion
     */
    boolean async() default true;
    
    /**
     * Custom correlation ID expression
     * Used to link related audit events across services
     */
    String correlationIdExpression() default "";
}