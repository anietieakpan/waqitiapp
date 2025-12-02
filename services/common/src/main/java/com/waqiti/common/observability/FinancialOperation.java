package com.waqiti.common.observability;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * CRITICAL FINANCIAL: Financial Operation Annotation
 * 
 * Marks methods that perform financial operations requiring comprehensive
 * tracing, correlation, and audit compliance.
 * 
 * When applied to a method, automatically enables:
 * - End-to-end distributed tracing
 * - Financial transaction correlation IDs
 * - PCI DSS compliance tracking
 * - Audit trail generation
 * - Fraud detection integration
 * - Performance monitoring
 * 
 * Example usage:
 * <pre>
 * {@code
 * @FinancialOperation(
 *     type = "PAYMENT",
 *     userIdParam = "userId",
 *     amountParam = "amount",
 *     currencyParam = "currency"
 * )
 * public PaymentResult processPayment(String userId, BigDecimal amount, String currency) {
 *     // Payment processing logic
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FinancialOperation {
    
    /**
     * Type of financial operation
     * Common values: PAYMENT, TRANSFER, DEPOSIT, WITHDRAWAL, REFUND
     */
    String type() default "";
    
    /**
     * Name of the parameter containing user ID
     * Used for extracting user context for tracing
     */
    String userIdParam() default "";
    
    /**
     * Name of the parameter containing transaction amount
     * Used for fraud detection and compliance thresholds
     */
    String amountParam() default "";
    
    /**
     * Name of the parameter containing currency code
     * Used for multi-currency compliance and reporting
     */
    String currencyParam() default "";
    
    /**
     * Name of the parameter containing payment method
     * Used for fraud detection and compliance analysis
     */
    String paymentMethodParam() default "";
    
    /**
     * Risk level of this operation
     * Used for determining tracing sampling and security measures
     */
    RiskLevel riskLevel() default RiskLevel.MEDIUM;
    
    /**
     * Whether this operation requires fraud detection
     */
    boolean requiresFraudCheck() default true;
    
    /**
     * Whether this operation requires compliance verification
     */
    boolean requiresComplianceCheck() default true;
    
    /**
     * Whether this operation requires enhanced audit logging
     */
    boolean requiresAuditTrail() default true;
    
    /**
     * Custom tags to add to the financial transaction trace
     */
    String[] customTags() default {};
    
    /**
     * Business domain for this operation
     * Used for business metrics and reporting
     */
    String businessDomain() default "payments";
    
    /**
     * Description of the financial operation
     * Used for audit trails and monitoring dashboards
     */
    String description() default "";
    
    /**
     * Risk levels for financial operations
     */
    enum RiskLevel {
        LOW,     // Small amounts, verified users, domestic transactions
        MEDIUM,  // Regular transactions, standard verification
        HIGH,    // Large amounts, international transfers, high-risk countries
        CRITICAL // Very large amounts, suspicious patterns, regulatory triggers
    }
}