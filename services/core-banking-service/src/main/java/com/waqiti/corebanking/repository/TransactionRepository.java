package com.waqiti.corebanking.repository;

import com.waqiti.corebanking.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Transaction Repository
 * 
 * Repository interface for Transaction entity operations
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * Find transaction by transaction number
     */
    Optional<Transaction> findByTransactionNumber(String transactionNumber);

    /**
     * Find transaction by idempotency key
     */
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    /**
     * Find transactions by source account with account details
     * Uses EntityGraph to prevent N+1 query problem
     */
    @EntityGraph(value = "Transaction.withAccounts", type = EntityGraph.EntityGraphType.FETCH)
    @Query("SELECT t FROM Transaction t WHERE t.sourceAccountId = :sourceAccountId ORDER BY t.transactionDate DESC")
    Page<Transaction> findBySourceAccountIdOrderByTransactionDateDesc(@Param("sourceAccountId") UUID sourceAccountId, Pageable pageable);

    /**
     * Find transactions by target account with account details
     * Uses EntityGraph to prevent N+1 query problem
     */
    @EntityGraph(value = "Transaction.withAccounts", type = EntityGraph.EntityGraphType.FETCH)
    @Query("SELECT t FROM Transaction t WHERE t.targetAccountId = :targetAccountId ORDER BY t.transactionDate DESC")
    Page<Transaction> findByTargetAccountIdOrderByTransactionDateDesc(@Param("targetAccountId") UUID targetAccountId, Pageable pageable);

    /**
     * Find transactions involving account (source or target) with account details
     * Uses EntityGraph to prevent N+1 query problem
     */
    @EntityGraph(value = "Transaction.withAccounts", type = EntityGraph.EntityGraphType.FETCH)
    @Query("SELECT DISTINCT t FROM Transaction t " +
           "WHERE t.sourceAccountId = :accountId OR t.targetAccountId = :accountId " +
           "ORDER BY t.transactionDate DESC")
    Page<Transaction> findByAccountId(@Param("accountId") UUID accountId, Pageable pageable);

    /**
     * Find transactions by status
     */
    List<Transaction> findByStatus(Transaction.TransactionStatus status);

    /**
     * Find transactions by type
     */
    List<Transaction> findByTransactionType(Transaction.TransactionType transactionType);

    /**
     * Find transactions by date range
     */
    @Query("SELECT t FROM Transaction t WHERE t.transactionDate BETWEEN :startDate AND :endDate ORDER BY t.transactionDate DESC")
    List<Transaction> findByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Find transactions by external reference
     */
    Optional<Transaction> findByExternalReference(String externalReference);

    /**
     * Find transactions requiring retry
     */
    @Query("SELECT t FROM Transaction t WHERE t.status = 'FAILED' AND t.retryCount < t.maxRetryAttempts")
    List<Transaction> findTransactionsRequiringRetry();

    /**
     * Find pending transactions
     */
    @Query("SELECT t FROM Transaction t WHERE t.status IN ('PENDING', 'AUTHORIZED', 'PROCESSING') AND t.transactionDate < :cutoffDate")
    List<Transaction> findStaleTransactions(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find transactions requiring approval
     */
    List<Transaction> findByStatusOrderByTransactionDateAsc(Transaction.TransactionStatus status);

    /**
     * Find transactions by batch ID with account details
     * Uses EntityGraph to prevent N+1 query problem
     */
    @EntityGraph(value = "Transaction.withAccounts", type = EntityGraph.EntityGraphType.FETCH)
    @Query("SELECT DISTINCT t FROM Transaction t " +
           "WHERE t.batchId = :batchId ORDER BY t.transactionDate ASC")
    List<Transaction> findByBatchIdOrderByTransactionDateAsc(@Param("batchId") UUID batchId);

    /**
     * Find high-value transactions
     */
    @Query("SELECT t FROM Transaction t WHERE t.amount > :threshold ORDER BY t.amount DESC")
    List<Transaction> findHighValueTransactions(@Param("threshold") BigDecimal threshold);

    /**
     * Find transactions by initiated by user with account details
     * Uses EntityGraph to prevent N+1 query problem
     */
    @EntityGraph(value = "Transaction.withAccounts", type = EntityGraph.EntityGraphType.FETCH)
    @Query("SELECT DISTINCT t FROM Transaction t " +
           "WHERE t.initiatedBy = :initiatedBy ORDER BY t.transactionDate DESC")
    Page<Transaction> findByInitiatedByOrderByTransactionDateDesc(@Param("initiatedBy") UUID initiatedBy, Pageable pageable);

    /**
     * Find reversible transactions
     */
    @Query("SELECT t FROM Transaction t WHERE t.status = 'COMPLETED' AND t.reversalTransactionId IS NULL")
    List<Transaction> findReversibleTransactions();

    /**
     * Find transactions on compliance hold
     */
    List<Transaction> findByStatusAndComplianceCheckIdIsNotNull(Transaction.TransactionStatus status);

    /**
     * Get transaction statistics for date range
     */
    @Query("SELECT t.transactionType, t.status, COUNT(t), SUM(t.amount) FROM Transaction t " +
           "WHERE t.transactionDate BETWEEN :startDate AND :endDate " +
           "GROUP BY t.transactionType, t.status")
    List<Object[]> getTransactionStatistics(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Find transactions by priority
     */
    List<Transaction> findByPriorityOrderByTransactionDateAsc(Transaction.TransactionPriority priority);

    /**
     * Find user's recent transactions with optimized joins
     * Uses EntityGraph to prevent N+1 query problem
     */
    @EntityGraph(value = "Transaction.withAccounts", type = EntityGraph.EntityGraphType.FETCH)
    @Query("SELECT DISTINCT t FROM Transaction t " +
           "LEFT JOIN Account sourceAcc ON sourceAcc.accountId = t.sourceAccountId " +
           "LEFT JOIN Account targetAcc ON targetAcc.accountId = t.targetAccountId " +
           "WHERE sourceAcc.userId = :userId OR targetAcc.userId = :userId " +
           "ORDER BY t.transactionDate DESC")
    Page<Transaction> findUserTransactions(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Count transactions by account in date range
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE (t.sourceAccountId = :accountId OR t.targetAccountId = :accountId) " +
           "AND t.transactionDate BETWEEN :startDate AND :endDate")
    long countAccountTransactionsInDateRange(
        @Param("accountId") UUID accountId, 
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Sum transaction amounts by account in date range
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN t.sourceAccountId = :accountId THEN -t.amount ELSE t.amount END), 0) " +
           "FROM Transaction t WHERE (t.sourceAccountId = :accountId OR t.targetAccountId = :accountId) " +
           "AND t.status = 'COMPLETED' AND t.transactionDate BETWEEN :startDate AND :endDate")
    BigDecimal sumAccountTransactionsInDateRange(
        @Param("accountId") UUID accountId, 
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find transactions by amount range
     */
    @Query("SELECT t FROM Transaction t WHERE t.amount BETWEEN :minAmount AND :maxAmount")
    List<Transaction> findByAmountRange(@Param("minAmount") BigDecimal minAmount, @Param("maxAmount") BigDecimal maxAmount);

    /**
     * Find suspicious transactions
     */
    @Query("SELECT t FROM Transaction t WHERE t.riskScore > :riskThreshold OR " +
           "(t.amount > :amountThreshold AND t.transactionType = 'P2P_TRANSFER')")
    List<Transaction> findSuspiciousTransactions(
        @Param("riskThreshold") Integer riskThreshold, 
        @Param("amountThreshold") BigDecimal amountThreshold
    );

    /**
     * Find incomplete transactions older than threshold
     */
    @Query("SELECT t FROM Transaction t WHERE t.status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED') " +
           "AND t.transactionDate < :cutoffDate")
    List<Transaction> findIncompleteTransactionsOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find transactions for reconciliation
     */
    @Query("SELECT t FROM Transaction t WHERE t.reconciliationId IS NULL AND t.status = 'COMPLETED' " +
           "AND t.transactionDate < :cutoffDate")
    List<Transaction> findTransactionsForReconciliation(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Count daily transactions by type
     */
    @Query("SELECT t.transactionType, COUNT(t) FROM Transaction t " +
           "WHERE DATE(t.transactionDate) = DATE(:date) " +
           "GROUP BY t.transactionType")
    List<Object[]> countDailyTransactionsByType(@Param("date") LocalDateTime date);

    /**
     * Find parent transaction with children and related accounts
     * Uses EntityGraph to prevent N+1 query problem
     */
    @EntityGraph(value = "Transaction.withAccountsAndParent", type = EntityGraph.EntityGraphType.FETCH)
    @Query("SELECT DISTINCT t FROM Transaction t " +
           "WHERE t.parentTransactionId = :parentId ORDER BY t.transactionDate")
    List<Transaction> findChildTransactions(@Param("parentId") UUID parentId);

    /**
     * Count transactions by account ID and created date range
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE (t.sourceAccountId = :accountId OR t.targetAccountId = :accountId) " +
           "AND t.createdAt BETWEEN :startDate AND :endDate")
    int countByAccountIdAndCreatedAtBetween(@Param("accountId") UUID accountId, 
                                           @Param("startDate") LocalDateTime startDate, 
                                           @Param("endDate") LocalDateTime endDate);

    /**
     * Find transactions by account ID and date range with account details
     * Uses EntityGraph to prevent N+1 query problem
     */
    @EntityGraph(value = "Transaction.withAccounts", type = EntityGraph.EntityGraphType.FETCH)
    @Query("SELECT DISTINCT t FROM Transaction t " +
           "WHERE (t.sourceAccountId = :accountId OR t.targetAccountId = :accountId) " +
           "AND t.createdAt BETWEEN :startDate AND :endDate ORDER BY t.createdAt DESC")
    List<Transaction> findByAccountIdAndDateRange(@Param("accountId") UUID accountId,
                                                 @Param("startDate") LocalDateTime startDate,
                                                 @Param("endDate") LocalDateTime endDate);
}