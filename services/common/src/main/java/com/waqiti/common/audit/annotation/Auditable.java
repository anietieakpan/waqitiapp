package com.waqiti.common.audit.annotation;

import com.waqiti.common.audit.AuditEventType;
import com.waqiti.common.audit.AuditSeverity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enhanced annotation for automatic audit logging using ComprehensiveAuditService
 * 
 * This annotation integrates with the existing ComprehensiveAuditService to provide
 * automated audit logging with compliance features and tamper detection.
 * 
 * USAGE EXAMPLES:
 * 
 * Financial Transactions:
 * @Auditable(
 *     eventType = "PAYMENT_PROCESSED",
 *     category = AuditEventType.PAYMENT_COMPLETED,
 *     severity = AuditSeverity.HIGH,
 *     pciRelevant = true,
 *     soxRelevant = true,
 *     description = "Credit card payment processed"
 * )
 * public PaymentResult processPayment(PaymentRequest request) { ... }
 * 
 * Authentication:
 * @Auditable(
 *     eventType = "USER_LOGIN",
 *     category = AuditEventType.LOGIN_SUCCESS,
 *     severity = AuditSeverity.INFO,
 *     description = "User authentication success"
 * )
 * public AuthResult authenticate(String username) { ... }
 * 
 * Data Access:
 * @Auditable(
 *     eventType = "PII_ACCESS",
 *     category = AuditEventType.DATA_READ,
 *     severity = AuditSeverity.MEDIUM,
 *     gdprRelevant = true,
 *     description = "Personal identifiable information accessed"
 * )
 * public UserProfile getUserProfile(String userId) { ... }
 * 
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2024-09-28
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    
    /**
     * Human-readable event type identifier
     * Used for categorization and reporting
     */
    String eventType();
    
    /**
     * Audit event category from predefined enum
     * Maps to existing ComprehensiveAuditService event types
     */
    AuditEventType category();
    
    /**
     * Severity level of the audit event
     * Used for alerting and prioritization
     */
    AuditSeverity severity() default AuditSeverity.INFO;
    
    /**
     * Description of the audited action
     * Supports placeholders: #{param}, #{result}, #{username}
     */
    String description() default "";
    
    /**
     * Resource identifier expression
     * SpEL expression to extract affected resource ID
     * Examples: "#request.userId", "#result.paymentId", "#walletId"
     */
    String affectedResourceExpression() default "";
    
    // Compliance Framework Flags
    
    /**
     * PCI DSS compliance relevance
     * Set true for operations involving payment card data
     */
    boolean pciRelevant() default false;
    
    /**
     * SOX compliance relevance
     * Set true for financial reporting and control activities
     */
    boolean soxRelevant() default false;
    
    /**
     * GDPR compliance relevance  
     * Set true for personal data processing activities
     */
    boolean gdprRelevant() default false;
    
    /**
     * SOC 2 compliance relevance
     * Set true for security and availability controls
     */
    boolean soc2Relevant() default false;
    
    /**
     * ISO 27001 compliance relevance
     * Set true for information security management activities
     */
    boolean iso27001Relevant() default false;
    
    // Advanced Features
    
    /**
     * Risk score (0-100) for fraud detection
     * Higher scores trigger additional security measures
     */
    int riskScore() default 0;
    
    /**
     * Whether to audit successful operations only
     * Set false to also audit failures and exceptions
     */
    boolean auditSuccessOnly() default false;
    
    /**
     * Whether to capture method parameters
     * Parameters are automatically sanitized
     */
    boolean captureParameters() default true;
    
    /**
     * Whether to capture method return values
     * Return values are automatically sanitized
     */
    boolean captureReturnValue() default false;
    
    /**
     * Whether to capture execution time
     * Useful for performance monitoring
     */
    boolean captureExecutionTime() default false;
    
    /**
     * Fields to exclude from capture
     * Sensitive data that should never be logged
     */
    String[] excludeFields() default {
        "password", "pin", "cvv", "ssn", "cardNumber", 
        "accountNumber", "routingNumber", "privateKey", "secret"
    };
    
    /**
     * Whether this event requires immediate investigation
     * Triggers security alerts and notifications
     */
    boolean requiresInvestigation() default false;
    
    /**
     * Custom retention period override
     * Format: "7_YEARS", "1_YEAR", "90_DAYS"
     * Overrides compliance-based defaults
     */
    String retentionPeriod() default "";
    
    /**
     * Whether to perform asynchronous audit logging
     * Set false for critical operations requiring synchronous audit
     */
    boolean async() default true;
    
    /**
     * Custom correlation ID expression
     * Links related audit events across services
     */
    String correlationIdExpression() default "";
    
    /**
     * Additional metadata expressions
     * Key-value pairs using SpEL expressions
     * Example: {"amount": "#request.amount", "currency": "#request.currency"}
     */
    String[] metadata() default {};
    
    /**
     * Business context identifier
     * Groups related business operations
     */
    String businessContext() default "";
    
    /**
     * Whether to send high-severity events to SIEM
     * Automatic for CRITICAL and HIGH severity events
     */
    boolean sendToSiem() default false;
    
    /**
     * Whether this operation modifies critical system state
     * Used for enhanced tamper detection
     */
    boolean criticalOperation() default false;
    
    /**
     * Regulatory requirement tags
     * Custom tags for specific regulatory requirements
     */
    String[] regulatoryTags() default {};
}