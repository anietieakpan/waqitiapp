package com.waqiti.database.annotation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.*;

/**
 * Financial Transaction Annotation
 * 
 * Use this annotation for all financial operations to ensure:
 * - SERIALIZABLE isolation level (prevents all concurrency issues)
 * - Proper timeout configuration
 * - Rollback on any exception
 * - Audit logging enabled
 * 
 * SECURITY: Enforces SERIALIZABLE isolation for financial operations
 * to prevent race conditions and ensure ACID compliance
 * 
 * Example usage:
 * @FinancialTransaction
 * public PaymentResult processPayment(PaymentRequest request) {
 *     // Financial operation code here
 * }
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Transactional(
    isolation = Isolation.SERIALIZABLE,  // SECURITY: Highest isolation level
    propagation = Propagation.REQUIRED,
    timeout = 30,                        // 30 second timeout
    rollbackFor = Exception.class,       // Rollback on any exception
    transactionManager = "financialTransactionManager"
)
public @interface FinancialTransaction {
    
    /**
     * Timeout in seconds for the transaction
     */
    @AliasFor(annotation = Transactional.class, attribute = "timeout")
    int timeout() default 30;
    
    /**
     * Whether the transaction should be read-only
     */
    @AliasFor(annotation = Transactional.class, attribute = "readOnly")
    boolean readOnly() default false;
    
    /**
     * Transaction label for auditing
     */
    String value() default "";
}