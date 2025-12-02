package com.waqiti.common.database;

import java.lang.annotation.*;

/**
 * Use Read Replica Annotation
 *
 * Marks a method to explicitly use read replica database.
 *
 * Use this annotation when:
 * 1. Method is not transactional but needs to read from replica
 * 2. You want to be explicit about replica usage
 * 3. Query performance is critical and should avoid primary DB
 *
 * Example:
 * <pre>
 * @UseReadReplica
 * public List<Transaction> getRecentTransactions(String userId) {
 *     return transactionRepository.findTop10ByUserIdOrderByCreatedAtDesc(userId);
 * }
 * </pre>
 *
 * Prefer @Transactional(readOnly = true) when possible, as it provides:
 * - Transaction boundaries
 * - Rollback capability
 * - Better integration with Spring
 *
 * Use @UseReadReplica for:
 * - Simple read operations
 * - Non-transactional methods
 * - Repository methods (where you can't add @Transactional)
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UseReadReplica {
}
