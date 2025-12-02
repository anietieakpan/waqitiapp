package com.waqiti.database.annotation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.*;

/**
 * Read-Only Transaction Annotation
 * 
 * Use this annotation for reporting and analytics operations to ensure:
 * - REPEATABLE_READ isolation level (consistent reads)
 * - Read-only optimization
 * - Longer timeout for complex queries
 * - No rollback needed (read-only)
 * 
 * SECURITY: Enforces REPEATABLE_READ isolation for consistent reporting
 * and prevents accidental modifications during read operations
 * 
 * Example usage:
 * @ReadOnlyTransaction
 * public List<TransactionSummary> getTransactionReport(ReportRequest request) {
 *     // Read-only operation code here
 * }
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Transactional(
    isolation = Isolation.REPEATABLE_READ, // SECURITY: Consistent reads
    propagation = Propagation.REQUIRED,
    timeout = 60,                          // 60 second timeout for reports
    readOnly = true,                       // Read-only optimization
    transactionManager = "readOnlyTransactionManager"
)
public @interface ReadOnlyTransaction {
    
    /**
     * Timeout in seconds for the transaction
     */
    @AliasFor(annotation = Transactional.class, attribute = "timeout")
    int timeout() default 60;
    
    /**
     * Transaction label for monitoring
     */
    String value() default "";
}