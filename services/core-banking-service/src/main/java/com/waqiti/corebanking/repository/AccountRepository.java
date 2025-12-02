package com.waqiti.corebanking.repository;

import com.waqiti.corebanking.domain.Account;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Account Repository
 * 
 * Repository interface for Account entity operations
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    /**
     * Find account by account number
     */
    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * Find accounts by user ID and status
     */
    List<Account> findByUserIdAndStatus(UUID userId, Account.AccountStatus status);

    /**
     * Find all accounts by user ID
     */
    List<Account> findByUserId(UUID userId);

    /**
     * Find user's primary wallet
     */
    @Query("SELECT a FROM Account a WHERE a.userId = :userId AND a.accountType = 'USER_WALLET' AND a.isPrimary = true")
    Optional<Account> findUserPrimaryWallet(@Param("userId") UUID userId);

    /**
     * Find system account by type
     */
    @Query("SELECT a FROM Account a WHERE a.accountType = :accountType AND a.userId IS NULL")
    Optional<Account> findSystemAccountByType(@Param("accountType") Account.AccountType accountType);

    /**
     * Find accounts by account type
     */
    List<Account> findByAccountType(Account.AccountType accountType);

    /**
     * Find accounts by status
     */
    List<Account> findByStatus(Account.AccountStatus status);

    /**
     * Find accounts by currency
     */
    List<Account> findByCurrency(String currency);

    /**
     * Find accounts by compliance level
     */
    List<Account> findByComplianceLevel(Account.ComplianceLevel complianceLevel);

    /**
     * Check if account number exists
     */
    boolean existsByAccountNumber(String accountNumber);

    /**
     * Find user accounts by type
     */
    @Query("SELECT a FROM Account a WHERE a.userId = :userId AND a.accountType = :accountType")
    List<Account> findByUserIdAndAccountType(@Param("userId") UUID userId, @Param("accountType") Account.AccountType accountType);

    /**
     * Find active user accounts
     */
    @Query("SELECT a FROM Account a WHERE a.userId = :userId AND a.status = 'ACTIVE'")
    List<Account> findActiveUserAccounts(@Param("userId") UUID userId);

    /**
     * Find system accounts
     */
    @Query("SELECT a FROM Account a WHERE a.userId IS NULL")
    List<Account> findSystemAccounts();

    /**
     * Find accounts requiring KYC update
     */
    @Query("SELECT a FROM Account a WHERE a.lastKycUpdate IS NULL OR a.lastKycUpdate < :cutoffDate")
    List<Account> findAccountsRequiringKycUpdate(@Param("cutoffDate") java.time.LocalDateTime cutoffDate);

    /**
     * Find dormant accounts
     */
    @Query("SELECT a FROM Account a WHERE a.lastTransactionDate < :cutoffDate AND a.status = 'ACTIVE'")
    List<Account> findDormantAccounts(@Param("cutoffDate") java.time.LocalDateTime cutoffDate);

    /**
     * Find accounts with low balance
     */
    @Query("SELECT a FROM Account a WHERE a.currentBalance < a.minimumBalance AND a.minimumBalance IS NOT NULL")
    List<Account> findAccountsWithLowBalance();

    /**
     * Count user accounts by type
     */
    @Query("SELECT COUNT(a) FROM Account a WHERE a.userId = :userId AND a.accountType = :accountType")
    long countUserAccountsByType(@Param("userId") UUID userId, @Param("accountType") Account.AccountType accountType);

    /**
     * Find accounts by external account ID
     */
    Optional<Account> findByExternalAccountId(String externalAccountId);

    /**
     * Find accounts eligible for interest calculation
     */
    @Query("SELECT a FROM Account a WHERE a.status = 'ACTIVE' " +
           "AND a.interestRate IS NOT NULL AND a.interestRate > 0 " +
           "AND (a.lastInterestCalculationDate IS NULL OR a.lastInterestCalculationDate < :cutoffDate) " +
           "AND a.accountType IN ('USER_SAVINGS', 'SAVINGS', 'FIXED_DEPOSIT', 'MONEY_MARKET') " +
           "ORDER BY a.accountNumber")
    List<Account> findEligibleForInterest(@Param("cutoffDate") LocalDate cutoffDate, Pageable pageable);

    /**
     * Find account by ID (UUID)
     */
    @Query("SELECT a FROM Account a WHERE a.accountId = :accountId")
    Optional<Account> findByAccountId(@Param("accountId") UUID accountId);

    /**
     * Find account by ID (String) - convenience method
     */
    @Query("SELECT a FROM Account a WHERE CAST(a.accountId AS string) = :accountId")
    Optional<Account> findByAccountId(@Param("accountId") String accountId);

    /**
     * Find accounts by user ID ordered by creation date desc (String userId)
     */
    @Query("SELECT a FROM Account a WHERE CAST(a.userId AS string) = :userId ORDER BY a.createdAt DESC")
    List<Account> findByUserIdOrderByCreatedAtDesc(@Param("userId") String userId);

    /**
     * Find account with lock for update (UUID)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountId = :accountId")
    Optional<Account> findByAccountIdWithLock(@Param("accountId") UUID accountId);

    /**
     * Find account with lock for update (String) - convenience method
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE CAST(a.accountId AS string) = :accountId")
    Optional<Account> findByAccountIdWithLock(@Param("accountId") String accountId);

    /**
     * Find accounts by search criteria
     */
    @Query("SELECT a FROM Account a WHERE " +
           "(:userId IS NULL OR CAST(a.userId AS string) = :userId) AND " +
           "(:accountType IS NULL OR a.accountType = :accountType) AND " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:currency IS NULL OR a.currency = :currency) AND " +
           "a.status != 'CLOSED' " +
           "ORDER BY a.createdAt DESC")
    org.springframework.data.domain.Page<Account> findByCriteria(
        @Param("userId") String userId,
        @Param("accountType") Account.AccountType accountType,
        @Param("status") Account.AccountStatus status,
        @Param("currency") String currency,
        org.springframework.data.domain.Pageable pageable);
}