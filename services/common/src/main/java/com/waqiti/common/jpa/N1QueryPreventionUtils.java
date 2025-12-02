package com.waqiti.common.jpa;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.data.jpa.repository.EntityGraph;

import jakarta.persistence.EntityManager;
import java.util.Collection;

/**
 * N+1 Query Prevention Utilities
 *
 * This utility class provides helper methods and guidelines to prevent N+1 query problems,
 * a common performance issue that can severely impact application performance.
 *
 * WHAT IS N+1 QUERY PROBLEM?
 * ===========================
 * When fetching a list of entities and then accessing their lazy-loaded relationships,
 * Hibernate executes:
 * - 1 query to fetch the parent entities
 * - N queries to fetch each child relationship (one per parent)
 *
 * Example BAD code:
 * <pre>
 * List<Transaction> transactions = transactionRepo.findByUserId(userId); // 1 query
 * for (Transaction txn : transactions) {
 *     Wallet wallet = txn.getWallet(); // N queries (one per transaction!)
 *     User user = txn.getUser();        // N queries (one per transaction!)
 * }
 * // For 100 transactions: 1 + 100 + 100 = 201 queries! ❌
 * </pre>
 *
 * SOLUTIONS PROVIDED:
 * ===================
 * 1. JOIN FETCH - Fetch associations in single query
 * 2. Entity Graphs - Define fetch strategies
 * 3. Batch Fetching - Fetch in batches instead of one-by-one
 * 4. DTO Projections - Fetch only needed data
 *
 * PERFORMANCE IMPACT:
 * ===================
 * Before: 201 queries, 500-1000ms latency
 * After: 1-3 queries, 10-50ms latency
 * Improvement: 10-50x faster
 */
@Slf4j
@UtilityClass
public class N1QueryPreventionUtils {

    /**
     * Check if a collection is initialized (not lazy-loaded)
     *
     * Use this to verify JOIN FETCH worked correctly in tests
     */
    public static boolean isInitialized(Object proxy) {
        return Hibernate.isInitialized(proxy);
    }

    /**
     * Initialize a lazy collection if not already initialized
     *
     * WARNING: Use sparingly - prefer JOIN FETCH in query
     */
    public static <T> Collection<T> initializeIfNeeded(Collection<T> collection) {
        if (!Hibernate.isInitialized(collection)) {
            log.warn("Lazy-loading collection - prefer JOIN FETCH in query for better performance");
            Hibernate.initialize(collection);
        }
        return collection;
    }

    /**
     * Best Practice Guide for Preventing N+1 Queries
     *
     * === SOLUTION 1: JOIN FETCH (RECOMMENDED) ===
     *
     * Use JOIN FETCH in JPQL queries to fetch associations eagerly:
     *
     * <pre>
     * @Query("SELECT t FROM Transaction t " +
     *        "LEFT JOIN FETCH t.wallet " +     // ← Fetches wallet in same query
     *        "LEFT JOIN FETCH t.user " +       // ← Fetches user in same query
     *        "WHERE t.userId = :userId")
     * List<Transaction> findByUserIdWithAssociations(@Param("userId") String userId);
     * </pre>
     *
     * Result: 1 query instead of 201 ✅
     *
     * Use cases:
     * - Fetching collections with known associations
     * - High-traffic endpoints
     * - Report generation
     *
     * === SOLUTION 2: Entity Graphs ===
     *
     * Define fetch strategies declaratively:
     *
     * <pre>
     * @EntityGraph(attributePaths = {"wallet", "user"})
     * @Query("SELECT t FROM Transaction t WHERE t.userId = :userId")
     * List<Transaction> findByUserId(@Param("userId") String userId);
     * </pre>
     *
     * Use cases:
     * - Multiple queries needing different fetch strategies
     * - Reusable fetch plans
     *
     * === SOLUTION 3: Batch Fetching ===
     *
     * Configure batch size in entity:
     *
     * <pre>
     * @Entity
     * @BatchSize(size = 25)  // Fetch 25 at a time instead of 1
     * public class Wallet { ... }
     * </pre>
     *
     * Result: 1 + ceil(N/25) queries instead of 1 + N
     *
     * Use cases:
     * - Cannot use JOIN FETCH (complex queries)
     * - Fallback optimization
     *
     * === SOLUTION 4: DTO Projections ===
     *
     * Fetch only needed fields using DTOs:
     *
     * <pre>
     * @Query("SELECT new com.waqiti.transaction.dto.TransactionSummary(" +
     *        "t.id, t.amount, t.status, w.balance) " +
     *        "FROM Transaction t " +
     *        "JOIN t.wallet w " +
     *        "WHERE t.userId = :userId")
     * List<TransactionSummary> findSummaryByUserId(@Param("userId") String userId);
     * </pre>
     *
     * Use cases:
     * - Read-only queries
     * - API responses (don't need full entities)
     * - Performance-critical operations
     *
     * === WHEN TO USE EACH SOLUTION ===
     *
     * JOIN FETCH:
     * ✅ Always fetch specific associations
     * ✅ Simple queries with 1-3 joins
     * ❌ Multiple collections (Hibernate limitation)
     * ❌ Pagination (can cause issues)
     *
     * Entity Graphs:
     * ✅ Multiple fetch strategies for same entity
     * ✅ Works with pagination
     * ❌ More complex to set up
     *
     * Batch Fetching:
     * ✅ Cannot use JOIN FETCH
     * ✅ Fallback optimization
     * ❌ Still multiple queries (just fewer)
     *
     * DTO Projections:
     * ✅ Read-only, high-performance
     * ✅ Only need subset of data
     * ❌ Cannot update entities
     *
     * === DETECTING N+1 QUERIES ===
     *
     * Enable Hibernate statistics in development:
     *
     * <pre>
     * spring:
     *   jpa:
     *     properties:
     *       hibernate:
     *         generate_statistics: true  # Shows query counts
     *
     * logging:
     *   level:
     *     org.hibernate.stat: DEBUG  # Logs statistics
     * </pre>
     *
     * Look for warnings like:
     * "HHH000104: firstResult/maxResults specified with collection fetch; applying in memory!"
     *
     * === TESTING FOR N+1 QUERIES ===
     *
     * Write tests that verify query count:
     *
     * <pre>
     * @Test
     * void testNoN1Queries() {
     *     // Arrange
     *     createTestData(100); // Create 100 transactions
     *
     *     // Act
     *     hibernateStats.clear();
     *     List<Transaction> transactions = transactionRepo.findByUserIdWithAssociations(userId);
     *
     *     // Access lazy relationships
     *     transactions.forEach(txn -> {
     *         txn.getWallet().getBalance(); // Should NOT trigger new query
     *         txn.getUser().getName();      // Should NOT trigger new query
     *     });
     *
     *     // Assert
     *     long queryCount = hibernateStats.getQueryExecutionCount();
     *     assertThat(queryCount).isLessThanOrEqualTo(3); // Allow 1-3 queries, not 201!
     * }
     * </pre>
     *
     * === COMMON PATTERNS IN WAQITI CODEBASE ===
     *
     * Transaction History (FIXED ✅):
     * <pre>
     * // transaction-service/TransactionRepository.java (Lines 175-179)
     * @Query("SELECT DISTINCT t FROM Transaction t " +
     *        "LEFT JOIN FETCH t.sourceAccount " +
     *        "LEFT JOIN FETCH t.targetAccount " +
     *        "WHERE (t.fromUserId = :userId OR t.toUserId = :userId)")
     * Page<Transaction> findByUserId(@Param("userId") String userId, Pageable pageable);
     * </pre>
     *
     * Payment with Merchant (Pattern to follow):
     * <pre>
     * @Query("SELECT p FROM Payment p " +
     *        "LEFT JOIN FETCH p.merchant " +
     *        "LEFT JOIN FETCH p.paymentMethod " +
     *        "WHERE p.id = :id")
     * Optional<Payment> findByIdWithDetails(@Param("id") UUID id);
     * </pre>
     *
     * Wallet with Transactions (DTO projection pattern):
     * <pre>
     * @Query("SELECT new com.waqiti.wallet.dto.WalletSummary(" +
     *        "w.id, w.balance, COUNT(t.id)) " +
     *        "FROM Wallet w " +
     *        "LEFT JOIN w.transactions t " +
     *        "WHERE w.userId = :userId " +
     *        "GROUP BY w.id, w.balance")
     * WalletSummary findSummaryByUserId(@Param("userId") UUID userId);
     * </pre>
     *
     * === RED FLAGS ===
     *
     * ❌ Loop accessing lazy relationships
     * ❌ FetchType.EAGER (loads always, even when not needed)
     * ❌ No @Query annotations on repository methods
     * ❌ High query counts in logs (100+)
     * ❌ Slow response times (500ms+) for simple queries
     *
     * === GREEN FLAGS ===
     *
     * ✅ JOIN FETCH in queries
     * ✅ @EntityGraph annotations
     * ✅ @BatchSize on entities
     * ✅ DTO projections for read-only
     * ✅ Hibernate statistics enabled in dev
     * ✅ Tests verifying query counts
     *
     * === PERFORMANCE MONITORING ===
     *
     * Add these metrics to track N+1 issues in production:
     *
     * - hibernate.query.execution.count
     * - hibernate.query.execution.max.time
     * - hibernate.cache.hit.ratio
     * - jpa.repository.method.execution.time
     *
     * Set alerts:
     * - Query count >10 for single endpoint call
     * - Query time >100ms for simple queries
     * - Cache hit ratio <80%
     */
    public static class BestPractices {
        // This is a documentation class, see methods above
    }

    /**
     * Example: Transaction history query with JOIN FETCH (RECOMMENDED PATTERN)
     */
    public static class ExampleQueries {
        /*
        // In TransactionRepository.java:

        @Query("SELECT DISTINCT t FROM Transaction t " +
               "LEFT JOIN FETCH t.wallet w " +
               "LEFT JOIN FETCH t.user u " +
               "WHERE t.userId = :userId " +
               "ORDER BY t.createdAt DESC")
        @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "30000"))
        List<Transaction> findByUserIdWithAssociations(@Param("userId") String userId);

        // Usage in service:
        @Transactional(readOnly = true)
        public List<TransactionDTO> getUserTransactionHistory(String userId) {
            List<Transaction> transactions = transactionRepo.findByUserIdWithAssociations(userId);

            return transactions.stream()
                .map(txn -> {
                    // No N+1 queries! Associations already loaded by JOIN FETCH
                    return TransactionDTO.builder()
                        .id(txn.getId())
                        .amount(txn.getAmount())
                        .walletBalance(txn.getWallet().getBalance())  // ✅ No query
                        .userName(txn.getUser().getName())             // ✅ No query
                        .build();
                })
                .collect(Collectors.toList());
        }
        */
    }
}
