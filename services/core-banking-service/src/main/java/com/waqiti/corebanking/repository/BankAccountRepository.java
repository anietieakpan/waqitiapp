package com.waqiti.corebanking.repository;

import com.waqiti.corebanking.domain.BankAccount;
import com.waqiti.corebanking.domain.BankAccount.BankAccountStatus;
import com.waqiti.corebanking.domain.BankAccount.BankAccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for BankAccount entity in core banking service.
 * 
 * Provides comprehensive data access operations for bank accounts including
 * verification, status management, and compliance tracking.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {
    
    /**
     * Find bank account by account number
     */
    Optional<BankAccount> findByAccountNumber(String accountNumber);
    
    /**
     * Find bank account by routing and account number
     */
    Optional<BankAccount> findByRoutingNumberAndAccountNumber(String routingNumber, String accountNumber);
    
    /**
     * Find bank accounts by user ID
     */
    List<BankAccount> findByUserId(UUID userId);
    
    /**
     * Find bank accounts by core account ID
     */
    List<BankAccount> findByCoreAccountId(UUID coreAccountId);
    
    /**
     * Find bank account by user ID and currency
     */
    Optional<BankAccount> findByUserIdAndCurrency(UUID userId, String currency);
    
    /**
     * Find active bank accounts by user ID
     */
    @Query("SELECT ba FROM BankAccount ba WHERE ba.userId = :userId AND ba.status = 'ACTIVE'")
    List<BankAccount> findActiveAccountsByUserId(@Param("userId") UUID userId);
    
    /**
     * Find bank accounts by account type
     */
    List<BankAccount> findByAccountType(BankAccountType accountType);
    
    /**
     * Find bank accounts by currency
     */
    List<BankAccount> findByCurrency(String currency);
    
    /**
     * Find bank accounts by status
     */
    List<BankAccount> findByStatus(BankAccountStatus status);
    
    /**
     * Find bank account by provider account ID
     */
    Optional<BankAccount> findByProviderAccountId(String providerAccountId);
    
    /**
     * Find bank accounts by provider name
     */
    List<BankAccount> findByProviderName(String providerName);
    
    /**
     * Check if bank account exists for user and currency
     */
    boolean existsByUserIdAndCurrencyAndStatus(UUID userId, String currency, BankAccountStatus status);
    
    /**
     * Count active accounts for user
     */
    @Query("SELECT COUNT(ba) FROM BankAccount ba WHERE ba.userId = :userId AND ba.status = 'ACTIVE'")
    long countActiveAccountsByUserId(@Param("userId") UUID userId);
    
    /**
     * Find bank accounts pending verification
     */
    @Query("SELECT ba FROM BankAccount ba WHERE ba.status IN ('PENDING_VERIFICATION', 'VERIFICATION_IN_PROGRESS') " +
           "AND ba.verificationDeadline > CURRENT_TIMESTAMP")
    List<BankAccount> findAccountsPendingVerification();
    
    /**
     * Find bank accounts with expired verification
     */
    @Query("SELECT ba FROM BankAccount ba WHERE ba.status IN ('PENDING_VERIFICATION', 'VERIFICATION_IN_PROGRESS') " +
           "AND ba.verificationDeadline <= CURRENT_TIMESTAMP")
    List<BankAccount> findAccountsWithExpiredVerification();
    
    /**
     * Find primary bank account for user
     */
    @Query("SELECT ba FROM BankAccount ba WHERE ba.userId = :userId AND ba.isPrimary = true AND ba.status = 'ACTIVE'")
    Optional<BankAccount> findPrimaryAccountByUserId(@Param("userId") UUID userId);
    
    /**
     * Find bank accounts requiring compliance review
     */
    @Query("SELECT ba FROM BankAccount ba WHERE ba.complianceStatus IN ('PENDING_REVIEW', 'REQUIRES_MANUAL_REVIEW')")
    List<BankAccount> findAccountsRequiringComplianceReview();
    
    /**
     * Find bank accounts by risk score range
     */
    @Query("SELECT ba FROM BankAccount ba WHERE ba.riskScore BETWEEN :minScore AND :maxScore")
    List<BankAccount> findByRiskScoreRange(@Param("minScore") Integer minScore, @Param("maxScore") Integer maxScore);
}