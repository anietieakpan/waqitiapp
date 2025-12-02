package com.waqiti.payment.repository;

import com.waqiti.payment.entity.LedgerAccount;
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
 * Repository for Ledger Account operations
 */
@Repository
public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {

    /**
     * Find account by account number with pessimistic write lock
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT la FROM LedgerAccount la WHERE la.accountNumber = :accountNumber")
    Optional<LedgerAccount> findByAccountNumberWithLock(@Param("accountNumber") String accountNumber);

    /**
     * Find account by account number
     */
    Optional<LedgerAccount> findByAccountNumber(String accountNumber);

    /**
     * Find accounts by owner
     */
    List<LedgerAccount> findByOwnerId(UUID ownerId);

    /**
     * Find accounts by type and status
     */
    List<LedgerAccount> findByAccountTypeAndStatus(
        LedgerAccount.AccountType accountType,
        LedgerAccount.AccountStatus status);

    /**
     * Find accounts by category
     */
    List<LedgerAccount> findByCategory(LedgerAccount.AccountCategory category);

    /**
     * Update account balance atomically
     */
    @Modifying
    @Query("UPDATE LedgerAccount la SET " +
           "la.balance = :newBalance, " +
           "la.availableBalance = :availableBalance, " +
           "la.updatedAt = :updatedAt, " +
           "la.version = la.version + 1 " +
           "WHERE la.id = :accountId AND la.version = :currentVersion")
    int updateBalanceWithOptimisticLock(
        @Param("accountId") UUID accountId,
        @Param("newBalance") BigDecimal newBalance,
        @Param("availableBalance") BigDecimal availableBalance,
        @Param("updatedAt") LocalDateTime updatedAt,
        @Param("currentVersion") Long currentVersion);

    /**
     * Update pending amounts
     */
    @Modifying
    @Query("UPDATE LedgerAccount la SET " +
           "la.pendingDebits = :pendingDebits, " +
           "la.pendingCredits = :pendingCredits, " +
           "la.updatedAt = :updatedAt " +
           "WHERE la.id = :accountId")
    void updatePendingAmounts(
        @Param("accountId") UUID accountId,
        @Param("pendingDebits") BigDecimal pendingDebits,
        @Param("pendingCredits") BigDecimal pendingCredits,
        @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * Find accounts needing reconciliation
     */
    @Query("SELECT la FROM LedgerAccount la WHERE " +
           "(la.isReconciled = false OR la.isReconciled IS NULL) " +
           "AND la.status = 'ACTIVE' " +
           "AND (la.lastReconciledAt IS NULL OR la.lastReconciledAt < :cutoffDate)")
    List<LedgerAccount> findAccountsNeedingReconciliation(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Calculate total balance by account type
     */
    @Query("SELECT SUM(la.balance) FROM LedgerAccount la " +
           "WHERE la.accountType = :accountType AND la.status = 'ACTIVE'")
    BigDecimal calculateTotalBalanceByType(@Param("accountType") LedgerAccount.AccountType accountType);

    /**
     * Find accounts with low balance
     */
    @Query("SELECT la FROM LedgerAccount la WHERE " +
           "la.status = 'ACTIVE' " +
           "AND la.minimumBalance IS NOT NULL " +
           "AND la.balance < la.minimumBalance")
    List<LedgerAccount> findAccountsWithLowBalance();

    /**
     * Find accounts exceeding credit limit
     */
    @Query("SELECT la FROM LedgerAccount la WHERE " +
           "la.accountType = 'LIABILITY' " +
           "AND la.status = 'ACTIVE' " +
           "AND la.creditLimit IS NOT NULL " +
           "AND ABS(la.balance) > la.creditLimit")
    List<LedgerAccount> findAccountsExceedingCreditLimit();

    /**
     * Lock multiple accounts for transaction
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT la FROM LedgerAccount la WHERE la.accountNumber IN :accountNumbers")
    List<LedgerAccount> lockAccountsForTransaction(@Param("accountNumbers") List<String> accountNumbers);

    /**
     * Update account status
     */
    @Modifying
    @Query("UPDATE LedgerAccount la SET la.status = :status, la.updatedAt = :updatedAt " +
           "WHERE la.id = :accountId")
    void updateAccountStatus(
        @Param("accountId") UUID accountId,
        @Param("status") LedgerAccount.AccountStatus status,
        @Param("updatedAt") LocalDateTime updatedAt);
}