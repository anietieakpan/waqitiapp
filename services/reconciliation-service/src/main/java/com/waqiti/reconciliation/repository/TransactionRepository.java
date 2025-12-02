package com.waqiti.reconciliation.repository;

import com.waqiti.reconciliation.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository for managing Transaction entities in reconciliation service
 * Provides specialized queries for reconciliation operations
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    /**
     * Find unreconciled transactions before a cutoff time
     */
    @Query("SELECT t FROM Transaction t WHERE t.reconciled = false AND t.createdAt < :cutoffTime ORDER BY t.createdAt ASC")
    List<Transaction> findUnreconciledBefore(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Find transactions by provider transaction ID
     */
    Optional<Transaction> findByProviderTransactionId(String providerTransactionId);

    /**
     * Find transactions by status and date range
     */
    @Query("SELECT t FROM Transaction t WHERE t.status = :status AND t.createdAt BETWEEN :startDate AND :endDate")
    List<Transaction> findByStatusAndDateRange(
            @Param("status") Transaction.TransactionStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find transactions by amount range
     */
    @Query("SELECT t FROM Transaction t WHERE t.amount BETWEEN :minAmount AND :maxAmount")
    List<Transaction> findByAmountBetween(
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount
    );

    /**
     * Find transactions by account ID and date range
     */
    @Query("SELECT t FROM Transaction t WHERE t.accountId = :accountId AND t.createdAt BETWEEN :startDate AND :endDate")
    List<Transaction> findByAccountIdAndDateRange(
            @Param("accountId") String accountId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find reconciled transactions by date range
     */
    @Query("SELECT t FROM Transaction t WHERE t.reconciled = true AND t.reconciledAt BETWEEN :startDate AND :endDate")
    List<Transaction> findReconciledBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Calculate account balances
     */
    @Query("SELECT t.accountId, SUM(CASE WHEN t.type = 'DEBIT' THEN -t.amount ELSE t.amount END) " +
           "FROM Transaction t WHERE t.reconciled = true GROUP BY t.accountId")
    Map<String, BigDecimal> calculateAccountBalances();

    /**
     * Find transactions by reference number
     */
    Optional<Transaction> findByReferenceNumber(String referenceNumber);

    /**
     * Find pending transactions older than specified hours
     */
    @Query("SELECT t FROM Transaction t WHERE t.status = 'PENDING' AND t.createdAt < :cutoffTime")
    List<Transaction> findPendingOlderThan(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Count unreconciled transactions
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.reconciled = false")
    long countUnreconciled();

    /**
     * Find transactions by currency and status
     */
    @Query("SELECT t FROM Transaction t WHERE t.currency = :currency AND t.status = :status")
    List<Transaction> findByCurrencyAndStatus(
            @Param("currency") String currency,
            @Param("status") Transaction.TransactionStatus status
    );

    /**
     * Find transactions for reconciliation matching
     */
    @Query("SELECT t FROM Transaction t WHERE t.reconciled = false " +
           "AND t.amount = :amount AND t.createdAt BETWEEN :startTime AND :endTime")
    List<Transaction> findForMatching(
            @Param("amount") BigDecimal amount,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * Find transactions by batch ID
     */
    @Query("SELECT t FROM Transaction t WHERE t.batchId = :batchId ORDER BY t.createdAt")
    List<Transaction> findByBatchId(@Param("batchId") String batchId);

    /**
     * Find suspicious transactions for reconciliation review
     */
    @Query("SELECT t FROM Transaction t WHERE t.reconciled = false " +
           "AND (t.amount > :highValueThreshold OR t.createdAt < :oldTransactionThreshold)")
    List<Transaction> findSuspiciousForReconciliation(
            @Param("highValueThreshold") BigDecimal highValueThreshold,
            @Param("oldTransactionThreshold") LocalDateTime oldTransactionThreshold
    );

    /**
     * Find transactions with pagination
     */
    @Query("SELECT t FROM Transaction t WHERE t.reconciled = :reconciled ORDER BY t.createdAt DESC")
    Page<Transaction> findByReconciledWithPaging(
            @Param("reconciled") boolean reconciled,
            Pageable pageable
    );

    /**
     * Update reconciliation status
     */
    @Query("UPDATE Transaction t SET t.reconciled = :reconciled, t.reconciledAt = :reconciledAt " +
           "WHERE t.id = :transactionId")
    void updateReconciliationStatus(
            @Param("transactionId") String transactionId,
            @Param("reconciled") boolean reconciled,
            @Param("reconciledAt") LocalDateTime reconciledAt
    );

    /**
     * Find duplicate transactions for reconciliation
     */
    @Query("SELECT t FROM Transaction t WHERE t.amount = :amount AND t.accountId = :accountId " +
           "AND t.createdAt BETWEEN :startTime AND :endTime AND t.id != :excludeId")
    List<Transaction> findPotentialDuplicates(
            @Param("amount") BigDecimal amount,
            @Param("accountId") String accountId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("excludeId") String excludeId
    );
}