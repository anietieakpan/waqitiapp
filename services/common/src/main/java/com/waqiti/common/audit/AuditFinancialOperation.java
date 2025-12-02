package com.waqiti.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * CRITICAL AUDIT ANNOTATION: Marks methods for automatic financial operation auditing
 * PRODUCTION-READY: Used by FinancialAuditAspect to automatically log financial operations
 * 
 * Usage:
 * @AuditFinancialOperation(
 *   operationType = "PAYMENT_PROCESSING",
 *   riskLevel = RiskLevel.HIGH,
 *   includeArguments = true,
 *   includeResult = false
 * )
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditFinancialOperation {
    
    /**
     * Type of financial operation being performed
     */
    String operationType() default "FINANCIAL_OPERATION";
    
    /**
     * Risk level of the operation for audit priority
     */
    RiskLevel riskLevel() default RiskLevel.MEDIUM;
    
    /**
     * Whether to include method arguments in audit log
     */
    boolean includeArguments() default true;
    
    /**
     * Whether to include method result in audit log
     */
    boolean includeResult() default false;
    
    /**
     * Whether this operation requires regulatory compliance audit
     */
    boolean requiresComplianceAudit() default false;
    
    /**
     * Additional audit tags for categorization
     */
    String[] tags() default {};
    
    /**
     * Description of the operation for audit purposes
     */
    String description() default "";
    
    /**
     * Risk level enumeration
     */
    enum RiskLevel {
        LOW,     // Basic operations, minimal audit requirements
        MEDIUM,  // Standard financial operations
        HIGH,    // Critical operations, full audit trail required
        CRITICAL // Highest risk operations, immediate alert required
    }
}