package com.waqiti.card.repository;

import com.waqiti.card.entity.CardTransaction;
import com.waqiti.card.enums.TransactionStatus;
import com.waqiti.card.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CardTransactionRepository - Spring Data JPA repository for CardTransaction entity
 *
 * Provides data access methods for transaction management including:
 * - Transaction lookup and queries
 * - Transaction history queries
 * - Financial reporting queries
 * - Fraud detection queries
 * - Settlement queries
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Repository
public interface CardTransactionRepository extends JpaRepository<CardTransaction, UUID>, JpaSpecificationExecutor<CardTransaction> {

    // ========================================================================
    // BASIC LOOKUPS
    // ========================================================================

    /**
     * Find transaction by transaction ID
     */
    Optional<CardTransaction> findByTransactionId(String transactionId);

    /**
     * Find transaction by external transaction ID
     */
    Optional<CardTransaction> findByExternalTransactionId(String externalTransactionId);

    /**
     * Find transaction by authorization code
     */
    List<CardTransaction> findByAuthorizationCode(String authorizationCode);

    /**
     * Find transaction by retrieval reference number
     */
    Optional<CardTransaction> findByRetrievalReferenceNumber(String rrn);

    /**
     * Check if transaction exists by transaction ID
     */
    boolean existsByTransactionId(String transactionId);

    // ========================================================================
    // CARD & USER QUERIES
    // ========================================================================

    /**
     * Find transactions by card ID
     */
    List<CardTransaction> findByCardId(UUID cardId);

    /**
     * Find transactions by card ID with pagination
     */
    Page<CardTransaction> findByCardId(UUID cardId, Pageable pageable);

    /**
     * Find transactions by user ID
     */
    List<CardTransaction> findByUserId(UUID userId);

    /**
     * Find transactions by user ID with pagination
     */
    Page<CardTransaction> findByUserId(UUID userId, Pageable pageable);

    /**
     * Find transactions by card ID and date range
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.cardId = :cardId AND " +
           "t.transactionDate BETWEEN :startDate AND :endDate AND t.deletedAt IS NULL " +
           "ORDER BY t.transactionDate DESC")
    List<CardTransaction> findByCardIdAndDateRange(
        @Param("cardId") UUID cardId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find transactions by user ID and date range
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.userId = :userId AND " +
           "t.transactionDate BETWEEN :startDate AND :endDate AND t.deletedAt IS NULL " +
           "ORDER BY t.transactionDate DESC")
    List<CardTransaction> findByUserIdAndDateRange(
        @Param("userId") UUID userId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    // ========================================================================
    // STATUS & TYPE QUERIES
    // ========================================================================

    /**
     * Find transactions by status
     */
    List<CardTransaction> findByTransactionStatus(TransactionStatus status);

    /**
     * Find transactions by type
     */
    List<CardTransaction> findByTransactionType(TransactionType type);

    /**
     * Find transactions by card ID and status
     */
    List<CardTransaction> findByCardIdAndTransactionStatus(UUID cardId, TransactionStatus status);

    /**
     * Find pending transactions
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.transactionStatus IN ('PENDING', 'AUTHORIZED', 'SETTLING') AND t.deletedAt IS NULL")
    List<CardTransaction> findPendingTransactions();

    /**
     * Find failed transactions
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.transactionStatus IN ('DECLINED', 'FAILED', 'TIMEOUT', 'FRAUD_BLOCKED') AND t.deletedAt IS NULL")
    List<CardTransaction> findFailedTransactions();

    /**
     * Find completed transactions
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.transactionStatus IN ('COMPLETED', 'SETTLED') AND t.deletedAt IS NULL")
    List<CardTransaction> findCompletedTransactions();

    // ========================================================================
    // AMOUNT & FINANCIAL QUERIES
    // ========================================================================

    /**
     * Find transactions by amount range
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.amount BETWEEN :minAmount AND :maxAmount AND t.deletedAt IS NULL")
    List<CardTransaction> findByAmountRange(
        @Param("minAmount") BigDecimal minAmount,
        @Param("maxAmount") BigDecimal maxAmount
    );

    /**
     * Find high-value transactions (above threshold)
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.amount > :threshold AND t.deletedAt IS NULL ORDER BY t.amount DESC")
    List<CardTransaction> findHighValueTransactions(@Param("threshold") BigDecimal threshold);

    /**
     * Calculate total transaction amount by card ID and date range
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM CardTransaction t WHERE " +
           "t.cardId = :cardId AND " +
           "t.transactionDate BETWEEN :startDate AND :endDate AND " +
           "t.transactionStatus IN ('COMPLETED', 'SETTLED') AND " +
           "t.deletedAt IS NULL")
    BigDecimal calculateTotalAmountByCardIdAndDateRange(
        @Param("cardId") UUID cardId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Calculate total transaction amount by user ID and date range
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM CardTransaction t WHERE " +
           "t.userId = :userId AND " +
           "t.transactionDate BETWEEN :startDate AND :endDate AND " +
           "t.transactionStatus IN ('COMPLETED', 'SETTLED') AND " +
           "t.deletedAt IS NULL")
    BigDecimal calculateTotalAmountByUserIdAndDateRange(
        @Param("userId") UUID userId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    // ========================================================================
    // MERCHANT QUERIES
    // ========================================================================

    /**
     * Find transactions by merchant ID
     */
    List<CardTransaction> findByMerchantId(String merchantId);

    /**
     * Find transactions by merchant category code
     */
    List<CardTransaction> findByMerchantCategoryCode(String mcc);

    /**
     * Find transactions by merchant country
     */
    List<CardTransaction> findByMerchantCountry(String countryCode);

    /**
     * Find transactions by card ID and merchant ID
     */
    List<CardTransaction> findByCardIdAndMerchantId(UUID cardId, String merchantId);

    // ========================================================================
    // FRAUD & RISK QUERIES
    // ========================================================================

    /**
     * Find transactions with high fraud score
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.fraudScore > :threshold AND t.deletedAt IS NULL ORDER BY t.fraudScore DESC")
    List<CardTransaction> findHighRiskTransactions(@Param("threshold") BigDecimal threshold);

    /**
     * Find fraud blocked transactions
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.transactionStatus = 'FRAUD_BLOCKED' AND t.deletedAt IS NULL")
    List<CardTransaction> findFraudBlockedTransactions();

    /**
     * Find transactions failed fraud check
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.fraudCheckPassed = false AND t.deletedAt IS NULL")
    List<CardTransaction> findTransactionsFailedFraudCheck();

    /**
     * Find transactions failed velocity check
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.velocityCheckPassed = false AND t.deletedAt IS NULL")
    List<CardTransaction> findTransactionsFailedVelocityCheck();

    /**
     * Find transactions requiring 3DS authentication
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.transactionStatus = 'PENDING_3DS' AND t.deletedAt IS NULL")
    List<CardTransaction> findTransactionsRequiring3DS();

    // ========================================================================
    // INTERNATIONAL & ONLINE QUERIES
    // ========================================================================

    /**
     * Find international transactions
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.isInternational = true AND t.deletedAt IS NULL")
    List<CardTransaction> findInternationalTransactions();

    /**
     * Find online transactions
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.isOnline = true AND t.deletedAt IS NULL")
    List<CardTransaction> findOnlineTransactions();

    /**
     * Find contactless transactions
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.isContactless = true AND t.deletedAt IS NULL")
    List<CardTransaction> findContactlessTransactions();

    /**
     * Find recurring transactions
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.isRecurring = true AND t.deletedAt IS NULL")
    List<CardTransaction> findRecurringTransactions();

    // ========================================================================
    // REVERSAL & DISPUTE QUERIES
    // ========================================================================

    /**
     * Find reversed transactions
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.isReversed = true AND t.deletedAt IS NULL")
    List<CardTransaction> findReversedTransactions();

    /**
     * Find disputed transactions
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.isDisputed = true AND t.deletedAt IS NULL")
    List<CardTransaction> findDisputedTransactions();

    /**
     * Find transaction by original transaction ID (for reversals)
     */
    List<CardTransaction> findByOriginalTransactionId(UUID originalTransactionId);

    // ========================================================================
    // SETTLEMENT QUERIES
    // ========================================================================

    /**
     * Find unsettled transactions
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.settlementDate IS NULL AND " +
           "t.transactionStatus IN ('COMPLETED', 'AUTHORIZED') AND t.deletedAt IS NULL")
    List<CardTransaction> findUnsettledTransactions();

    /**
     * Find transactions by settlement date range
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.settlementDate BETWEEN :startDate AND :endDate AND t.deletedAt IS NULL")
    List<CardTransaction> findBySettlementDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find transactions by batch ID
     */
    List<CardTransaction> findByBatchId(String batchId);

    // ========================================================================
    // VELOCITY & PATTERN QUERIES
    // ========================================================================

    /**
     * Count transactions by card ID in time window
     */
    @Query("SELECT COUNT(t) FROM CardTransaction t WHERE t.cardId = :cardId AND " +
           "t.transactionDate > :sinceDate AND t.deletedAt IS NULL")
    long countByCardIdSince(@Param("cardId") UUID cardId, @Param("sinceDate") LocalDateTime sinceDate);

    /**
     * Count transactions by user ID in time window
     */
    @Query("SELECT COUNT(t) FROM CardTransaction t WHERE t.userId = :userId AND " +
           "t.transactionDate > :sinceDate AND t.deletedAt IS NULL")
    long countByUserIdSince(@Param("userId") UUID userId, @Param("sinceDate") LocalDateTime sinceDate);

    /**
     * Find recent transactions by card ID
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.cardId = :cardId AND " +
           "t.transactionDate > :sinceDate AND t.deletedAt IS NULL " +
           "ORDER BY t.transactionDate DESC")
    List<CardTransaction> findRecentTransactionsByCardId(
        @Param("cardId") UUID cardId,
        @Param("sinceDate") LocalDateTime sinceDate
    );

    /**
     * Find duplicate transactions (same card, merchant, amount within time window)
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.cardId = :cardId AND " +
           "t.merchantId = :merchantId AND t.amount = :amount AND " +
           "t.transactionDate BETWEEN :startDate AND :endDate AND " +
           "t.transactionId != :excludeTransactionId AND t.deletedAt IS NULL")
    List<CardTransaction> findPotentialDuplicates(
        @Param("cardId") UUID cardId,
        @Param("merchantId") String merchantId,
        @Param("amount") BigDecimal amount,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        @Param("excludeTransactionId") String excludeTransactionId
    );

    // ========================================================================
    // STATISTICAL QUERIES
    // ========================================================================

    /**
     * Count transactions by card ID
     */
    long countByCardId(UUID cardId);

    /**
     * Count transactions by status
     */
    long countByTransactionStatus(TransactionStatus status);

    /**
     * Count transactions by type
     */
    long countByTransactionType(TransactionType type);

    /**
     * Get transaction statistics by status
     */
    @Query("SELECT t.transactionStatus, COUNT(t) FROM CardTransaction t WHERE t.deletedAt IS NULL GROUP BY t.transactionStatus")
    List<Object[]> getTransactionStatisticsByStatus();

    /**
     * Get transaction statistics by type
     */
    @Query("SELECT t.transactionType, COUNT(t) FROM CardTransaction t WHERE t.deletedAt IS NULL GROUP BY t.transactionType")
    List<Object[]> getTransactionStatisticsByType();

    /**
     * Get daily transaction volume
     */
    @Query("SELECT DATE(t.transactionDate), COUNT(t), SUM(t.amount) FROM CardTransaction t WHERE " +
           "t.transactionDate BETWEEN :startDate AND :endDate AND " +
           "t.transactionStatus IN ('COMPLETED', 'SETTLED') AND " +
           "t.deletedAt IS NULL " +
           "GROUP BY DATE(t.transactionDate) ORDER BY DATE(t.transactionDate)")
    List<Object[]> getDailyTransactionVolume(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    // ========================================================================
    // SOFT DELETE QUERIES
    // ========================================================================

    /**
     * Find deleted transactions
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.deletedAt IS NOT NULL")
    List<CardTransaction> findDeletedTransactions();
}
