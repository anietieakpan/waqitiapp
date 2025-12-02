package com.waqiti.account.repository;

import com.waqiti.account.domain.Account;
import com.waqiti.account.domain.Account.AccountStatus;
import com.waqiti.account.domain.Account.AccountType;
import com.waqiti.common.repository.BaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Account entity operations
 * 
 * Extends BaseRepository to inherit common operations while adding
 * account-specific queries and operations.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Repository
public interface AccountRepository extends BaseRepository<Account, UUID> {
    
    /**
     * Find account by account number
     */
    @Query("SELECT a FROM Account a WHERE a.accountNumber = :accountNumber AND a.deleted = false")
    Optional<Account> findByAccountNumber(@Param("accountNumber") String accountNumber);
    
    /**
     * Find account by account number with pessimistic lock for transactions
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountNumber = :accountNumber AND a.deleted = false")
    Optional<Account> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);
    
    /**
     * Find all accounts for a user
     */
    @Query("SELECT a FROM Account a WHERE a.userId = :userId AND a.deleted = false ORDER BY a.createdAt DESC")
    List<Account> findByUserId(@Param("userId") UUID userId);
    
    /**
     * Find all active accounts for a user
     */
    @Query("SELECT a FROM Account a WHERE a.userId = :userId AND a.status = 'ACTIVE' " +
           "AND a.deleted = false AND a.frozen = false ORDER BY a.createdAt DESC")
    List<Account> findActiveAccountsByUserId(@Param("userId") UUID userId);
    
    /**
     * Find accounts by type
     */
    @Query("SELECT a FROM Account a WHERE a.accountType = :accountType AND a.deleted = false")
    Page<Account> findByAccountType(@Param("accountType") AccountType accountType, Pageable pageable);
    
    /**
     * Find accounts by status
     */
    @Query("SELECT a FROM Account a WHERE a.status = :status AND a.deleted = false")
    Page<Account> findByStatus(@Param("status") AccountStatus status, Pageable pageable);
    
    /**
     * Find accounts with balance greater than specified amount
     */
    @Query("SELECT a FROM Account a WHERE a.balance > :amount AND a.deleted = false")
    List<Account> findAccountsWithBalanceGreaterThan(@Param("amount") BigDecimal amount);
    
    /**
     * Find accounts with low balance
     */
    @Query("SELECT a FROM Account a WHERE a.balance < :threshold AND a.status = 'ACTIVE' " +
           "AND a.deleted = false ORDER BY a.balance ASC")
    List<Account> findLowBalanceAccounts(@Param("threshold") BigDecimal threshold);
    
    /**
     * Find dormant accounts (no transactions for specified period)
     */
    @Query("SELECT a FROM Account a WHERE a.lastTransactionAt < :date OR a.lastTransactionAt IS NULL " +
           "AND a.status = 'ACTIVE' AND a.deleted = false")
    List<Account> findDormantAccounts(@Param("date") LocalDateTime date);
    
    /**
     * Find accounts requiring KYC verification
     */
    @Query("SELECT a FROM Account a WHERE a.kycVerified = false AND a.status != 'CLOSED' " +
           "AND a.deleted = false")
    List<Account> findAccountsRequiringKYC();
    
    /**
     * Find sub-accounts for a parent account
     */
    @Query("SELECT a FROM Account a WHERE a.parentAccount.id = :parentId AND a.deleted = false")
    List<Account> findSubAccounts(@Param("parentId") UUID parentId);
    
    /**
     * Update account balance
     */
    @Modifying
    @Transactional
    @Query("UPDATE Account a SET a.balance = :balance, a.availableBalance = :availableBalance, " +
           "a.lastTransactionAt = :transactionDate WHERE a.id = :accountId")
    int updateBalance(@Param("accountId") UUID accountId, 
                     @Param("balance") BigDecimal balance,
                     @Param("availableBalance") BigDecimal availableBalance,
                     @Param("transactionDate") LocalDateTime transactionDate);
    
    /**
     * Update daily spent amount
     */
    @Modifying
    @Transactional
    @Query("UPDATE Account a SET a.dailySpent = a.dailySpent + :amount WHERE a.id = :accountId")
    int incrementDailySpent(@Param("accountId") UUID accountId, @Param("amount") BigDecimal amount);
    
    /**
     * Update monthly spent amount
     */
    @Modifying
    @Transactional
    @Query("UPDATE Account a SET a.monthlySpent = a.monthlySpent + :amount WHERE a.id = :accountId")
    int incrementMonthlySpent(@Param("accountId") UUID accountId, @Param("amount") BigDecimal amount);
    
    /**
     * Reset daily spending limits for all accounts
     */
    @Modifying
    @Transactional
    @Query("UPDATE Account a SET a.dailySpent = 0 WHERE a.deleted = false")
    int resetDailySpending();
    
    /**
     * Reset monthly spending limits for all accounts
     */
    @Modifying
    @Transactional
    @Query("UPDATE Account a SET a.monthlySpent = 0 WHERE a.deleted = false")
    int resetMonthlySpending();
    
    /**
     * Freeze account
     */
    @Modifying
    @Transactional
    @Query("UPDATE Account a SET a.frozen = true, a.freezeReason = :reason, " +
           "a.frozenAt = :frozenAt, a.status = 'FROZEN' WHERE a.id = :accountId")
    int freezeAccount(@Param("accountId") UUID accountId, 
                     @Param("reason") String reason,
                     @Param("frozenAt") LocalDateTime frozenAt);
    
    /**
     * Unfreeze account
     */
    @Modifying
    @Transactional
    @Query("UPDATE Account a SET a.frozen = false, a.freezeReason = null, " +
           "a.frozenAt = null, a.status = 'ACTIVE' WHERE a.id = :accountId")
    int unfreezeAccount(@Param("accountId") UUID accountId);
    
    /**
     * Update account status
     */
    @Modifying
    @Transactional
    @Query("UPDATE Account a SET a.status = :status WHERE a.id = :accountId")
    int updateAccountStatus(@Param("accountId") UUID accountId, 
                           @Param("status") AccountStatus status);
    
    /**
     * Update KYC level
     */
    @Modifying
    @Transactional
    @Query("UPDATE Account a SET a.kycVerified = :verified, a.kycVerifiedDate = :verifiedAt " +
           "WHERE a.accountId = :accountId")
    int updateKycLevel(@Param("accountId") UUID accountId, 
                      @Param("verified") Boolean verified,
                      @Param("verifiedAt") LocalDateTime verifiedAt);
    
    /**
     * Place hold on account funds
     */
    @Modifying
    @Transactional
    @Query("UPDATE Account a SET a.availableBalance = a.availableBalance - :amount " +
           "WHERE a.id = :accountId AND a.availableBalance >= :amount")
    int placeHold(@Param("accountId") UUID accountId, @Param("amount") BigDecimal amount);
    
    /**
     * Release hold on account funds
     */
    @Modifying
    @Transactional
    @Query("UPDATE Account a SET a.availableBalance = a.availableBalance + :amount " +
           "WHERE a.id = :accountId")
    int releaseHold(@Param("accountId") UUID accountId, @Param("amount") BigDecimal amount);
    
    /**
     * Count accounts by status
     */
    @Query("SELECT COUNT(a) FROM Account a WHERE a.status = :status AND a.deleted = false")
    long countByStatus(@Param("status") AccountStatus status);
    
    /**
     * Count accounts by type
     */
    @Query("SELECT COUNT(a) FROM Account a WHERE a.accountType = :accountType AND a.deleted = false")
    long countByAccountType(@Param("accountType") AccountType accountType);
    
    /**
     * Calculate total balance across all accounts
     */
    @Query("SELECT SUM(a.balance) FROM Account a WHERE a.deleted = false AND a.status = 'ACTIVE'")
    BigDecimal calculateTotalBalance();
    
    /**
     * Calculate total balance for a user
     */
    @Query("SELECT SUM(a.balance) FROM Account a WHERE a.userId = :userId " +
           "AND a.deleted = false AND a.status = 'ACTIVE'")
    BigDecimal calculateUserTotalBalance(@Param("userId") UUID userId);
    
    /**
     * Find accounts with expiring cards (payment methods)
     */
    @Query("SELECT DISTINCT a FROM Account a JOIN a.paymentMethods pm " +
           "WHERE pm.expiryDate BETWEEN :startDate AND :endDate " +
           "AND pm.status = 'VERIFIED' AND a.deleted = false")
    List<Account> findAccountsWithExpiringCards(@Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate);
    
    /**
     * Find high-value accounts
     */
    @Query("SELECT a FROM Account a WHERE a.balance >= :threshold " +
           "AND a.tierLevel IN ('VIP', 'PLATINUM') AND a.deleted = false " +
           "ORDER BY a.balance DESC")
    List<Account> findHighValueAccounts(@Param("threshold") BigDecimal threshold);
    
    /**
     * Check if account number exists
     */
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Account a " +
           "WHERE a.accountNumber = :accountNumber")
    boolean existsByAccountNumber(@Param("accountNumber") String accountNumber);
    
    /**
     * Find accounts for risk assessment
     */
    @Query("SELECT a FROM Account a WHERE a.riskScore > :riskThreshold " +
           "OR a.frozen = true OR a.status = 'SUSPENDED' AND a.deleted = false")
    List<Account> findHighRiskAccounts(@Param("riskThreshold") Integer riskThreshold);
    
    /**
     * Update ledger balance (end of day processing)
     */
    @Modifying
    @Transactional
    @Query("UPDATE Account a SET a.ledgerBalance = a.balance WHERE a.deleted = false")
    int updateLedgerBalances();
    
    /**
     * Find accounts eligible for interest calculation
     */
    @Query("SELECT a FROM Account a WHERE a.accountType = 'USER_SAVINGS' " +
           "AND a.status = 'ACTIVE' AND a.deleted = false")
    List<Account> findAccountsForInterestCalculation(@Param("date") LocalDateTime date);
    
    /**
     * Find accounts by user ID and status
     */
    List<Account> findByUserIdAndStatus(UUID userId, AccountStatus status);
    
    /**
     * Find accounts by criteria for search
     */
    @Query("SELECT a FROM Account a WHERE " +
           "(:userId IS NULL OR a.userId = :userId) AND " +
           "(:accountType IS NULL OR a.accountType = :accountType) AND " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:currency IS NULL OR a.currency = :currency) AND " +
           "(:minBalance IS NULL OR a.currentBalance >= :minBalance) AND " +
           "(:maxBalance IS NULL OR a.currentBalance <= :maxBalance) AND " +
           "a.deleted = false")
    Page<Account> findAccountsByCriteria(@Param("userId") UUID userId,
                                        @Param("accountType") AccountType accountType,
                                        @Param("status") AccountStatus status,
                                        @Param("currency") String currency,
                                        @Param("minBalance") BigDecimal minBalance,
                                        @Param("maxBalance") BigDecimal maxBalance,
                                        Pageable pageable);
    
    /**
     * Find accounts for compliance review
     */
    @Query("SELECT a FROM Account a WHERE a.complianceLevel = 'ENHANCED' " +
           "OR a.complianceLevel = 'RESTRICTED' AND a.deleted = false")
    List<Account> findAccountsForComplianceReview();
    
    /**
     * Get account count by type
     */
    @Query("SELECT a.accountType, COUNT(a) FROM Account a WHERE a.deleted = false GROUP BY a.accountType")
    List<Object[]> getAccountCountByType();
    
    /**
     * Get account count by currency
     */
    @Query("SELECT a.currency, COUNT(a) FROM Account a WHERE a.deleted = false GROUP BY a.currency")
    List<Object[]> getAccountCountByCurrency();
    
    /**
     * Get total system balance
     */
    @Query("SELECT SUM(a.currentBalance) FROM Account a WHERE a.deleted = false")
    BigDecimal getTotalSystemBalance();
}