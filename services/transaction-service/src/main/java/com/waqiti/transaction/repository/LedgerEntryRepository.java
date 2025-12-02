package com.waqiti.transaction.repository;

import com.waqiti.transaction.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for Ledger Entry Management
 *
 * Implements double-entry bookkeeping data access layer.
 * CRITICAL: This repository was missing and causing runtime NullPointerException.
 *
 * Compliance:
 * - SOX: Complete audit trail of all ledger entries
 * - PCI-DSS: Secure financial data access
 * - GAAP: Double-entry bookkeeping enforcement
 *
 * @author Waqiti Platform Team
 * @since 2025-10-31 - CRITICAL FIX
 */
@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    /**
     * Find all ledger entries for a specific transaction
     * Used for ledger verification and reconciliation
     */
    @Query("SELECT l FROM LedgerEntry l WHERE l.transactionId = :transactionId ORDER BY l.createdAt ASC")
    List<LedgerEntry> findByTransactionId(@Param("transactionId") UUID transactionId);

    /**
     * Find all ledger entries for a batch
     * Used for batch reconciliation and rollback
     */
    @Query("SELECT l FROM LedgerEntry l WHERE l.batchId = :batchId ORDER BY l.sequenceNumber ASC")
    List<LedgerEntry> findByBatchId(@Param("batchId") String batchId);

    /**
     * Find all ledger entries for an account within date range
     * Used for account statement generation
     */
    @Query("SELECT l FROM LedgerEntry l WHERE l.accountId = :accountId " +
           "AND l.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY l.createdAt ASC")
    List<LedgerEntry> findByAccountIdAndDateRange(
        @Param("accountId") String accountId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Calculate account balance at a specific point in time
     * Uses balance_after for performance (running balance)
     */
    @Query("SELECT l.balanceAfter FROM LedgerEntry l " +
           "WHERE l.accountId = :accountId " +
           "AND l.createdAt <= :asOfDate " +
           "ORDER BY l.createdAt DESC " +
           "LIMIT 1")
    BigDecimal getAccountBalanceAsOf(
        @Param("accountId") String accountId,
        @Param("asOfDate") LocalDateTime asOfDate
    );

    /**
     * Verify double-entry balance for a transaction
     * Sum of debits must equal sum of credits
     */
    @Query("SELECT " +
           "SUM(CASE WHEN l.entryType = 'DEBIT' THEN l.amount ELSE 0 END) as totalDebits, " +
           "SUM(CASE WHEN l.entryType = 'CREDIT' THEN l.amount ELSE 0 END) as totalCredits " +
           "FROM LedgerEntry l WHERE l.transactionId = :transactionId")
    Object[] verifyDoubleEntryBalance(@Param("transactionId") UUID transactionId);

    /**
     * Find orphaned ledger entries (no matching transaction)
     * Used for data integrity checks
     */
    @Query("SELECT l FROM LedgerEntry l WHERE l.transactionId NOT IN " +
           "(SELECT t.id FROM Transaction t)")
    List<LedgerEntry> findOrphanedEntries();

    /**
     * Get latest ledger entry for an account
     * Used to get current balance efficiently
     */
    @Query("SELECT l FROM LedgerEntry l WHERE l.accountId = :accountId " +
           "ORDER BY l.createdAt DESC LIMIT 1")
    LedgerEntry findLatestByAccountId(@Param("accountId") String accountId);

    /**
     * Find entries by entry type (DEBIT or CREDIT)
     */
    @Query("SELECT l FROM LedgerEntry l WHERE l.accountId = :accountId " +
           "AND l.entryType = :entryType " +
           "AND l.createdAt BETWEEN :startDate AND :endDate")
    List<LedgerEntry> findByAccountIdAndEntryType(
        @Param("accountId") String accountId,
        @Param("entryType") String entryType,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Count entries for a batch (verification)
     */
    @Query("SELECT COUNT(l) FROM LedgerEntry l WHERE l.batchId = :batchId")
    long countByBatchId(@Param("batchId") String batchId);

    /**
     * Find entries requiring reconciliation
     * Entries older than X days without reconciliation flag
     */
    @Query("SELECT l FROM LedgerEntry l WHERE l.reconciledAt IS NULL " +
           "AND l.createdAt < :cutoffDate")
    List<LedgerEntry> findUnreconciledEntries(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Get sum of all debits for an account in a period
     */
    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM LedgerEntry l " +
           "WHERE l.accountId = :accountId " +
           "AND l.entryType = 'DEBIT' " +
           "AND l.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal sumDebitsByAccountAndPeriod(
        @Param("accountId") String accountId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Get sum of all credits for an account in a period
     */
    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM LedgerEntry l " +
           "WHERE l.accountId = :accountId " +
           "AND l.entryType = 'CREDIT' " +
           "AND l.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal sumCreditsByAccountAndPeriod(
        @Param("accountId") String accountId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find entries by reference ID (for cross-referencing)
     */
    @Query("SELECT l FROM LedgerEntry l WHERE l.referenceId = :referenceId")
    List<LedgerEntry> findByReferenceId(@Param("referenceId") String referenceId);

    /**
     * Verify ledger integrity - all transactions have balanced entries
     * Returns list of transaction IDs with unbalanced entries
     */
    @Query("SELECT l.transactionId FROM LedgerEntry l " +
           "GROUP BY l.transactionId " +
           "HAVING SUM(CASE WHEN l.entryType = 'DEBIT' THEN l.amount ELSE -l.amount END) <> 0")
    List<UUID> findUnbalancedTransactions();

    /**
     * Get entries for period-end closing
     */
    @Query("SELECT l FROM LedgerEntry l " +
           "WHERE l.createdAt BETWEEN :startDate AND :endDate " +
           "AND l.accountId LIKE :accountPattern " +
           "ORDER BY l.accountId, l.createdAt")
    List<LedgerEntry> findForPeriodClosing(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        @Param("accountPattern") String accountPattern
    );
}
