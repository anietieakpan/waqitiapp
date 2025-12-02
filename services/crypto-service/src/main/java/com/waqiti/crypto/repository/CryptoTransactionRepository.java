/**
 * Crypto Transaction Repository
 * JPA repository for cryptocurrency transaction operations
 */
package com.waqiti.crypto.repository;

import com.waqiti.crypto.entity.CryptoTransaction;
import com.waqiti.crypto.entity.CryptoCurrency;
import com.waqiti.crypto.entity.CryptoTransactionStatus;
import com.waqiti.crypto.entity.CryptoTransactionType;
import com.waqiti.crypto.entity.RiskLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CryptoTransactionRepository extends JpaRepository<CryptoTransaction, UUID> {

    /**
     * Find transaction by ID and user ID (security check)
     */
    Optional<CryptoTransaction> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Find all transactions for user
     */
    Page<CryptoTransaction> findByUserId(UUID userId, Pageable pageable);

    /**
     * Find transactions by user and currency
     */
    Page<CryptoTransaction> findByUserIdAndCurrency(UUID userId, CryptoCurrency currency, Pageable pageable);

    /**
     * Find transactions by user and type
     */
    Page<CryptoTransaction> findByUserIdAndTransactionType(UUID userId, CryptoTransactionType transactionType, Pageable pageable);

    /**
     * Find transactions by user with filters
     */
    @Query("SELECT t FROM CryptoTransaction t WHERE t.userId = :userId " +
           "AND (:currency IS NULL OR t.currency = :currency) " +
           "AND (:transactionType IS NULL OR t.transactionType = :transactionType) " +
           "ORDER BY t.createdAt DESC")
    Page<CryptoTransaction> findByUserIdWithFilters(
        @Param("userId") UUID userId,
        @Param("currency") CryptoCurrency currency,
        @Param("transactionType") CryptoTransactionType transactionType,
        Pageable pageable
    );

    /**
     * Find transaction by transaction hash
     */
    Optional<CryptoTransaction> findByTxHash(String txHash);

    /**
     * Find transactions by wallet ID
     */
    List<CryptoTransaction> findByWalletId(UUID walletId);

    /**
     * Find transactions by status
     */
    List<CryptoTransaction> findByStatus(CryptoTransactionStatus status);

    /**
     * Find transactions by user ID and status list (for compliance freezing)
     */
    List<CryptoTransaction> findByUserIdAndStatusIn(UUID userId, List<CryptoTransactionStatus> statuses);

    /**
     * Find transactions requiring approval
     */
    List<CryptoTransaction> findByApprovalRequiredTrue();

    /**
     * Find transactions requiring review
     */
    List<CryptoTransaction> findByReviewRequiredTrue();

    /**
     * Find scheduled transactions ready for processing
     */
    @Query("SELECT t FROM CryptoTransaction t WHERE t.scheduledFor IS NOT NULL AND t.scheduledFor <= :currentTime AND t.status = 'PENDING_DELAY'")
    List<CryptoTransaction> findScheduledTransactionsReadyForProcessing(@Param("currentTime") LocalDateTime currentTime);

    /**
     * Find high-risk transactions
     */
    List<CryptoTransaction> findByRiskLevel(RiskLevel riskLevel);

    /**
     * Find transactions with high risk scores
     */
    @Query("SELECT t FROM CryptoTransaction t WHERE t.riskScore >= :minRiskScore ORDER BY t.riskScore DESC")
    List<CryptoTransaction> findHighRiskTransactions(@Param("minRiskScore") BigDecimal minRiskScore);

    /**
     * Get daily transaction total for user and currency
     */
    @Query("SELECT COALESCE(SUM(t.usdValue), 0) FROM CryptoTransaction t " +
           "WHERE t.userId = :userId AND t.currency = :currency " +
           "AND t.createdAt >= :startOfDay AND t.createdAt < :endOfDay " +
           "AND t.status NOT IN ('FAILED', 'CANCELLED')")
    BigDecimal getDailyTransactionTotal(
        @Param("userId") UUID userId,
        @Param("currency") CryptoCurrency currency,
        @Param("startOfDay") LocalDateTime startOfDay,
        @Param("endOfDay") LocalDateTime endOfDay
    );

    /**
     * Count transactions in time period
     */
    @Query("SELECT COUNT(t) FROM CryptoTransaction t WHERE t.userId = :userId " +
           "AND t.createdAt >= :startTime AND t.createdAt < :endTime")
    long countTransactionsInPeriod(
        @Param("userId") UUID userId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    /**
     * Get transaction volume for user in period
     */
    @Query("SELECT COALESCE(SUM(t.usdValue), 0) FROM CryptoTransaction t " +
           "WHERE t.userId = :userId AND t.createdAt >= :startTime AND t.createdAt < :endTime " +
           "AND t.status = 'COMPLETED'")
    BigDecimal getTransactionVolumeInPeriod(
        @Param("userId") UUID userId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    /**
     * Find recent transactions to same address
     */
    @Query("SELECT t FROM CryptoTransaction t WHERE t.userId = :userId AND t.toAddress = :toAddress " +
           "AND t.createdAt >= :sinceTime ORDER BY t.createdAt DESC")
    List<CryptoTransaction> findRecentTransactionsToAddress(
        @Param("userId") UUID userId,
        @Param("toAddress") String toAddress,
        @Param("sinceTime") LocalDateTime sinceTime
    );

    /**
     * Update transaction confirmations
     */
    @Modifying
    @Query("UPDATE CryptoTransaction t SET t.confirmations = :confirmations, t.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE t.id = :transactionId")
    int updateConfirmations(@Param("transactionId") UUID transactionId, @Param("confirmations") Integer confirmations);

    /**
     * Mark transaction as confirmed
     */
    @Modifying
    @Query("UPDATE CryptoTransaction t SET t.status = 'CONFIRMED', t.confirmedAt = :confirmedAt, " +
           "t.confirmations = :confirmations, t.updatedAt = CURRENT_TIMESTAMP WHERE t.id = :transactionId")
    int markAsConfirmed(
        @Param("transactionId") UUID transactionId,
        @Param("confirmedAt") LocalDateTime confirmedAt,
        @Param("confirmations") Integer confirmations
    );

    /**
     * Mark transaction as completed
     */
    @Modifying
    @Query("UPDATE CryptoTransaction t SET t.status = 'COMPLETED', t.completedAt = :completedAt, " +
           "t.updatedAt = CURRENT_TIMESTAMP WHERE t.id = :transactionId")
    int markAsCompleted(@Param("transactionId") UUID transactionId, @Param("completedAt") LocalDateTime completedAt);

    /**
     * Update transaction status and block info
     */
    @Modifying
    @Query("UPDATE CryptoTransaction t SET t.status = :status, t.blockNumber = :blockNumber, " +
           "t.blockHash = :blockHash, t.updatedAt = CURRENT_TIMESTAMP WHERE t.id = :transactionId")
    int updateBlockInfo(
        @Param("transactionId") UUID transactionId,
        @Param("status") CryptoTransactionStatus status,
        @Param("blockNumber") Long blockNumber,
        @Param("blockHash") String blockHash
    );

    /**
     * Get transaction statistics for user
     */
    @Query("SELECT t.transactionType, t.currency, COUNT(t), COALESCE(SUM(t.usdValue), 0), COALESCE(AVG(t.usdValue), 0) " +
           "FROM CryptoTransaction t WHERE t.userId = :userId AND t.status = 'COMPLETED' " +
           "GROUP BY t.transactionType, t.currency")
    List<Object[]> getTransactionStatisticsByUserId(@Param("userId") UUID userId);

    /**
     * Find failed transactions for retry
     */
    @Query("SELECT t FROM CryptoTransaction t WHERE t.status = 'FAILED' AND t.failedAt >= :sinceTime " +
           "AND t.failureReason NOT LIKE '%PERMANENT%' ORDER BY t.failedAt ASC")
    List<CryptoTransaction> findFailedTransactionsForRetry(@Param("sinceTime") LocalDateTime sinceTime);

    /**
     * Clean up old completed transactions
     */
    @Modifying
    @Query("DELETE FROM CryptoTransaction t WHERE t.status = 'COMPLETED' AND t.completedAt < :cutoffDate")
    int deleteOldCompletedTransactions(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find transaction by event ID (for idempotency in event-driven processing)
     * Used by CryptoWithdrawalEventConsumer to prevent duplicate processing
     */
    Optional<CryptoTransaction> findByEventId(String eventId);

    /**
     * Find transaction by blockchain transaction hash
     * Used to retrieve transaction details after blockchain broadcast
     */
    Optional<CryptoTransaction> findByBlockchainTxHash(String blockchainTxHash);

    /**
     * Sum withdrawals by user, currency, and date range
     * Used to enforce daily withdrawal limits
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM CryptoTransaction t " +
           "WHERE t.userId = :userId AND t.currency = :currency " +
           "AND t.transactionType = 'WITHDRAWAL' " +
           "AND t.createdAt >= :startTime AND t.createdAt < :endTime " +
           "AND t.status NOT IN ('FAILED', 'CANCELLED', 'REJECTED')")
    BigDecimal sumWithdrawalsByUserAndCurrencyAndDateRange(
        @Param("userId") UUID userId,
        @Param("currency") CryptoCurrency currency,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
}