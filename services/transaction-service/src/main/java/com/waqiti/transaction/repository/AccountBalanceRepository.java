package com.waqiti.transaction.repository;

import com.waqiti.transaction.domain.AccountBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Account Balance Management
 *
 * Manages account balances with pessimistic locking to prevent race conditions.
 * CRITICAL: This repository was missing and causing runtime NullPointerException.
 *
 * Features:
 * - Pessimistic write locks for balance updates
 * - Optimistic locking with @Version
 * - Atomic balance calculations
 * - Balance snapshot capabilities
 *
 * @author Waqiti Platform Team
 * @since 2025-10-31 - CRITICAL FIX
 */
@Repository
public interface AccountBalanceRepository extends JpaRepository<AccountBalance, UUID> {

    /**
     * Find account balance by account ID
     */
    @Query("SELECT ab FROM AccountBalance ab WHERE ab.accountId = :accountId")
    Optional<AccountBalance> findByAccountId(@Param("accountId") String accountId);

    /**
     * Find account balance with pessimistic write lock
     * Prevents concurrent modifications during balance updates
     *
     * CRITICAL for preventing race conditions in:
     * - Payment processing
     * - Fund transfers
     * - Balance updates
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ab FROM AccountBalance ab WHERE ab.accountId = :accountId")
    Optional<AccountBalance> findByAccountIdForUpdate(@Param("accountId") String accountId);

    /**
     * Find all balances for a user
     * Used for multi-account users
     */
    @Query("SELECT ab FROM AccountBalance ab WHERE ab.userId = :userId")
    List<AccountBalance> findByUserId(@Param("userId") UUID userId);

    /**
     * Find balances by currency
     */
    @Query("SELECT ab FROM AccountBalance ab WHERE ab.currency = :currency")
    List<AccountBalance> findByCurrency(@Param("currency") String currency);

    /**
     * Find balances below minimum threshold
     * Used for low balance alerts
     */
    @Query("SELECT ab FROM AccountBalance ab WHERE ab.availableBalance < :threshold " +
           "AND ab.lowBalanceAlertEnabled = true")
    List<AccountBalance> findLowBalanceAccounts(@Param("threshold") BigDecimal threshold);

    /**
     * Find balances with negative values (overdraft detection)
     */
    @Query("SELECT ab FROM AccountBalance ab WHERE ab.availableBalance < 0")
    List<AccountBalance> findOverdraftAccounts();

    /**
     * Find balances with pending reserves
     */
    @Query("SELECT ab FROM AccountBalance ab WHERE ab.reservedBalance > 0")
    List<AccountBalance> findAccountsWithReserves();

    /**
     * Update available balance atomically
     * Uses optimistic locking with version field
     *
     * @return number of rows updated (should be 1 on success, 0 on version conflict)
     */
    @Modifying
    @Query("UPDATE AccountBalance ab SET " +
           "ab.availableBalance = ab.availableBalance + :amount, " +
           "ab.lastModifiedAt = :timestamp, " +
           "ab.version = ab.version + 1 " +
           "WHERE ab.accountId = :accountId " +
           "AND ab.version = :expectedVersion")
    int updateAvailableBalanceAtomic(
        @Param("accountId") String accountId,
        @Param("amount") BigDecimal amount,
        @Param("timestamp") LocalDateTime timestamp,
        @Param("expectedVersion") Long expectedVersion
    );

    /**
     * Reserve funds atomically
     * Decreases available balance and increases reserved balance
     */
    @Modifying
    @Query("UPDATE AccountBalance ab SET " +
           "ab.availableBalance = ab.availableBalance - :amount, " +
           "ab.reservedBalance = ab.reservedBalance + :amount, " +
           "ab.lastModifiedAt = :timestamp, " +
           "ab.version = ab.version + 1 " +
           "WHERE ab.accountId = :accountId " +
           "AND ab.availableBalance >= :amount " +
           "AND ab.version = :expectedVersion")
    int reserveFundsAtomic(
        @Param("accountId") String accountId,
        @Param("amount") BigDecimal amount,
        @Param("timestamp") LocalDateTime timestamp,
        @Param("expectedVersion") Long expectedVersion
    );

    /**
     * Release reserved funds atomically
     * Increases available balance and decreases reserved balance
     */
    @Modifying
    @Query("UPDATE AccountBalance ab SET " +
           "ab.availableBalance = ab.availableBalance + :amount, " +
           "ab.reservedBalance = ab.reservedBalance - :amount, " +
           "ab.lastModifiedAt = :timestamp, " +
           "ab.version = ab.version + 1 " +
           "WHERE ab.accountId = :accountId " +
           "AND ab.reservedBalance >= :amount " +
           "AND ab.version = :expectedVersion")
    int releaseFundsAtomic(
        @Param("accountId") String accountId,
        @Param("amount") BigDecimal amount,
        @Param("timestamp") LocalDateTime timestamp,
        @Param("expectedVersion") Long expectedVersion
    );

    /**
     * Confirm reserved funds (convert to actual debit)
     * Decreases reserved balance without changing available balance
     */
    @Modifying
    @Query("UPDATE AccountBalance ab SET " +
           "ab.reservedBalance = ab.reservedBalance - :amount, " +
           "ab.lastModifiedAt = :timestamp, " +
           "ab.version = ab.version + 1 " +
           "WHERE ab.accountId = :accountId " +
           "AND ab.reservedBalance >= :amount " +
           "AND ab.version = :expectedVersion")
    int confirmReservedFundsAtomic(
        @Param("accountId") String accountId,
        @Param("amount") BigDecimal amount,
        @Param("timestamp") LocalDateTime timestamp,
        @Param("expectedVersion") Long expectedVersion
    );

    /**
     * Get total balance (available + reserved + pending)
     */
    @Query("SELECT ab.availableBalance + ab.reservedBalance + ab.pendingBalance " +
           "FROM AccountBalance ab WHERE ab.accountId = :accountId")
    BigDecimal getTotalBalance(@Param("accountId") String accountId);

    /**
     * Find accounts requiring balance snapshot
     * Used for end-of-day balance recording
     */
    @Query("SELECT ab FROM AccountBalance ab WHERE ab.lastSnapshotAt IS NULL " +
           "OR ab.lastSnapshotAt < :cutoffDate")
    List<AccountBalance> findAccountsRequiringSnapshot(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find accounts with discrepancies (ledger balance != account balance)
     * Used for reconciliation
     */
    @Query("SELECT ab FROM AccountBalance ab WHERE ab.reconciledAt < :cutoffDate")
    List<AccountBalance> findAccountsRequiringReconciliation(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Get aggregate balance statistics
     */
    @Query("SELECT " +
           "SUM(ab.availableBalance) as totalAvailable, " +
           "SUM(ab.reservedBalance) as totalReserved, " +
           "SUM(ab.pendingBalance) as totalPending, " +
           "COUNT(ab) as accountCount " +
           "FROM AccountBalance ab WHERE ab.currency = :currency")
    Object[] getBalanceStatistics(@Param("currency") String currency);

    /**
     * Find dormant accounts (no activity in X days)
     */
    @Query("SELECT ab FROM AccountBalance ab WHERE ab.lastModifiedAt < :cutoffDate " +
           "AND ab.availableBalance > 0")
    List<AccountBalance> findDormantAccounts(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Check if account has sufficient funds
     * Returns true if available balance >= required amount
     */
    @Query("SELECT CASE WHEN ab.availableBalance >= :amount THEN true ELSE false END " +
           "FROM AccountBalance ab WHERE ab.accountId = :accountId")
    boolean hasSufficientFunds(
        @Param("accountId") String accountId,
        @Param("amount") BigDecimal amount
    );

    /**
     * Get accounts by balance range
     */
    @Query("SELECT ab FROM AccountBalance ab WHERE ab.availableBalance BETWEEN :minBalance AND :maxBalance")
    List<AccountBalance> findByBalanceRange(
        @Param("minBalance") BigDecimal minBalance,
        @Param("maxBalance") BigDecimal maxBalance
    );

    /**
     * Update last reconciled timestamp
     */
    @Modifying
    @Query("UPDATE AccountBalance ab SET ab.reconciledAt = :timestamp " +
           "WHERE ab.accountId = :accountId")
    int updateReconciledTimestamp(
        @Param("accountId") String accountId,
        @Param("timestamp") LocalDateTime timestamp
    );

    /**
     * Reset daily limits (scheduled job)
     */
    @Modifying
    @Query("UPDATE AccountBalance ab SET " +
           "ab.dailyDebitTotal = 0, " +
           "ab.dailyTransactionCount = 0, " +
           "ab.limitResetDate = :resetDate")
    int resetDailyLimits(@Param("resetDate") LocalDateTime resetDate);

    /**
     * Increment daily transaction counters
     */
    @Modifying
    @Query("UPDATE AccountBalance ab SET " +
           "ab.dailyDebitTotal = ab.dailyDebitTotal + :amount, " +
           "ab.dailyTransactionCount = ab.dailyTransactionCount + 1 " +
           "WHERE ab.accountId = :accountId")
    int incrementDailyCounters(
        @Param("accountId") String accountId,
        @Param("amount") BigDecimal amount
    );
}
