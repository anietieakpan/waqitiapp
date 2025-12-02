package com.waqiti.savings.repository;

import com.waqiti.savings.domain.SavingsAccount;
import com.waqiti.savings.domain.SavingsAccount.AccountType;
import com.waqiti.savings.domain.SavingsAccount.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for SavingsAccount entity operations.
 * Provides comprehensive query methods for account management with optimistic locking support.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2025-11-19
 */
@Repository
public interface SavingsAccountRepository extends JpaRepository<SavingsAccount, UUID>,
                                                   JpaSpecificationExecutor<SavingsAccount> {

    /**
     * Find account by account number.
     * Account numbers are unique identifiers for savings accounts.
     *
     * @param accountNumber the unique account number
     * @return Optional containing the account if found
     */
    Optional<SavingsAccount> findByAccountNumber(String accountNumber);

    /**
     * Find all accounts for a specific user.
     * Returns accounts ordered by creation date (newest first).
     *
     * @param userId the user's UUID
     * @return list of savings accounts for the user
     */
    @Query("SELECT sa FROM SavingsAccount sa WHERE sa.userId = :userId ORDER BY sa.createdAt DESC")
    List<SavingsAccount> findByUserId(@Param("userId") UUID userId);

    /**
     * Find accounts by user ID with pagination support.
     *
     * @param userId the user's UUID
     * @param pageable pagination parameters
     * @return paginated list of savings accounts
     */
    Page<SavingsAccount> findByUserId(UUID userId, Pageable pageable);

    /**
     * Find accounts by user ID and status.
     * Useful for filtering active, suspended, or closed accounts.
     *
     * @param userId the user's UUID
     * @param status the account status
     * @return list of accounts matching criteria
     */
    List<SavingsAccount> findByUserIdAndStatus(UUID userId, Status status);

    /**
     * Find accounts by user ID and account type.
     *
     * @param userId the user's UUID
     * @param accountType the type of savings account
     * @return list of accounts matching criteria
     */
    List<SavingsAccount> findByUserIdAndAccountType(UUID userId, AccountType accountType);

    /**
     * Find active accounts for a user.
     * Convenience method for most common query.
     *
     * @param userId the user's UUID
     * @return list of active savings accounts
     */
    @Query("SELECT sa FROM SavingsAccount sa WHERE sa.userId = :userId AND sa.status = 'ACTIVE' ORDER BY sa.createdAt DESC")
    List<SavingsAccount> findActiveAccountsByUserId(@Param("userId") UUID userId);

    /**
     * Find account with pessimistic write lock for concurrent update protection.
     * Use this when performing balance updates to prevent race conditions.
     *
     * @param id the account UUID
     * @return Optional containing the locked account
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT sa FROM SavingsAccount sa WHERE sa.id = :id")
    Optional<SavingsAccount> findByIdWithLock(@Param("id") UUID id);

    /**
     * Find accounts that require interest calculation.
     * Returns accounts where next calculation date is in the past.
     *
     * @param now current timestamp
     * @return list of accounts due for interest calculation
     */
    @Query("SELECT sa FROM SavingsAccount sa WHERE sa.status = 'ACTIVE' " +
           "AND sa.nextInterestCalculationAt IS NOT NULL " +
           "AND sa.nextInterestCalculationAt <= :now")
    List<SavingsAccount> findAccountsDueForInterestCalculation(@Param("now") LocalDateTime now);

    /**
     * Find accounts that should auto-sweep (transfer excess balance).
     * Returns accounts where current balance exceeds sweep threshold.
     *
     * @return list of accounts ready for auto-sweep
     */
    @Query("SELECT sa FROM SavingsAccount sa WHERE sa.status = 'ACTIVE' " +
           "AND sa.autoSweepEnabled = true " +
           "AND sa.autoSweepThreshold IS NOT NULL " +
           "AND sa.balance > sa.autoSweepThreshold")
    List<SavingsAccount> findAccountsForAutoSweep();

    /**
     * Find accounts with balance below minimum required.
     * Useful for generating low balance notifications.
     *
     * @param userId the user's UUID
     * @return list of accounts below minimum balance
     */
    @Query("SELECT sa FROM SavingsAccount sa WHERE sa.userId = :userId " +
           "AND sa.status = 'ACTIVE' " +
           "AND sa.balance < sa.minimumBalance")
    List<SavingsAccount> findAccountsBelowMinimumBalance(@Param("userId") UUID userId);

    /**
     * Count total accounts for a user.
     * Used for enforcing max accounts per user limit.
     *
     * @param userId the user's UUID
     * @return count of accounts
     */
    @Query("SELECT COUNT(sa) FROM SavingsAccount sa WHERE sa.userId = :userId")
    Long countByUserId(@Param("userId") UUID userId);

    /**
     * Count active accounts for a user.
     *
     * @param userId the user's UUID
     * @return count of active accounts
     */
    Long countByUserIdAndStatus(UUID userId, Status status);

    /**
     * Check if account number exists.
     * Used for uniqueness validation during account creation.
     *
     * @param accountNumber the account number to check
     * @return true if account number exists
     */
    boolean existsByAccountNumber(String accountNumber);

    /**
     * Get total balance across all active accounts for a user.
     * Useful for analytics and reporting.
     *
     * @param userId the user's UUID
     * @return sum of all active account balances
     */
    @Query("SELECT COALESCE(SUM(sa.balance), 0) FROM SavingsAccount sa " +
           "WHERE sa.userId = :userId AND sa.status = 'ACTIVE'")
    BigDecimal getTotalBalanceByUserId(@Param("userId") UUID userId);

    /**
     * Get total interest earned across all accounts for a user.
     *
     * @param userId the user's UUID
     * @return sum of total interest earned
     */
    @Query("SELECT COALESCE(SUM(sa.totalInterestEarned), 0) FROM SavingsAccount sa " +
           "WHERE sa.userId = :userId")
    BigDecimal getTotalInterestEarnedByUserId(@Param("userId") UUID userId);

    /**
     * Find accounts created within a date range.
     * Useful for reporting and analytics.
     *
     * @param startDate start of date range
     * @param endDate end of date range
     * @param pageable pagination parameters
     * @return paginated list of accounts
     */
    @Query("SELECT sa FROM SavingsAccount sa WHERE sa.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY sa.createdAt DESC")
    Page<SavingsAccount> findAccountsCreatedBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    /**
     * Find accounts by currency.
     * Useful for multi-currency support.
     *
     * @param currency ISO currency code (e.g., USD, EUR)
     * @return list of accounts in specified currency
     */
    List<SavingsAccount> findByCurrency(String currency);

    /**
     * Find accounts with overdraft enabled.
     *
     * @param userId the user's UUID
     * @return list of accounts with overdraft protection
     */
    @Query("SELECT sa FROM SavingsAccount sa WHERE sa.userId = :userId " +
           "AND sa.overdraftEnabled = true " +
           "AND sa.status = 'ACTIVE'")
    List<SavingsAccount> findAccountsWithOverdraft(@Param("userId") UUID userId);

    /**
     * Find accounts that exceeded withdrawal limit this month.
     * Used for enforcing withdrawal restrictions.
     *
     * @param userId the user's UUID
     * @return list of accounts that hit withdrawal limit
     */
    @Query("SELECT sa FROM SavingsAccount sa WHERE sa.userId = :userId " +
           "AND sa.monthlyWithdrawalCountLimit IS NOT NULL " +
           "AND sa.currentMonthWithdrawals >= sa.monthlyWithdrawalCountLimit")
    List<SavingsAccount> findAccountsAtWithdrawalLimit(@Param("userId") UUID userId);

    /**
     * Delete all accounts for a user (soft delete by setting status).
     * GDPR compliance - right to be forgotten.
     * NOTE: Actual implementation should soft-delete, not hard-delete.
     *
     * @param userId the user's UUID
     */
    @Query("UPDATE SavingsAccount sa SET sa.status = 'CLOSED', sa.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE sa.userId = :userId")
    void softDeleteAllByUserId(@Param("userId") UUID userId);

    /**
     * Find accounts pending closure.
     * Accounts in PENDING_CLOSURE status awaiting final settlement.
     *
     * @return list of accounts pending closure
     */
    @Query("SELECT sa FROM SavingsAccount sa WHERE sa.status = 'PENDING_CLOSURE'")
    List<SavingsAccount> findAccountsPendingClosure();

    /**
     * Get aggregate account statistics for a user.
     * Returns total balance, total deposits, total withdrawals, interest earned.
     *
     * @param userId the user's UUID
     * @return account statistics projection
     */
    @Query("SELECT NEW map(" +
           "COALESCE(SUM(sa.balance), 0) as totalBalance, " +
           "COALESCE(SUM(sa.totalDeposits), 0) as totalDeposits, " +
           "COALESCE(SUM(sa.totalWithdrawals), 0) as totalWithdrawals, " +
           "COALESCE(SUM(sa.totalInterestEarned), 0) as totalInterestEarned, " +
           "COUNT(sa) as accountCount) " +
           "FROM SavingsAccount sa WHERE sa.userId = :userId AND sa.status = 'ACTIVE'")
    Optional<java.util.Map<String, Object>> getAccountStatistics(@Param("userId") UUID userId);
}
