package com.waqiti.payment.repository;

import com.waqiti.payment.entity.NFCTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for NFC transactions
 */
@Repository
public interface NFCTransactionRepository extends JpaRepository<NFCTransaction, Long> {

    /**
     * Find transaction by transaction ID
     */
    Optional<NFCTransaction> findByTransactionId(String transactionId);

    /**
     * Check if payment ID already exists
     */
    boolean existsByPaymentId(String paymentId);

    /**
     * Check if transfer ID already exists
     */
    boolean existsByTransferId(String transferId);

    /**
     * Find transactions by customer ID
     */
    Page<NFCTransaction> findByCustomerIdOrderByCreatedAtDesc(String customerId, Pageable pageable);

    /**
     * Find transactions by merchant ID
     */
    Page<NFCTransaction> findByMerchantIdOrderByCreatedAtDesc(String merchantId, Pageable pageable);

    /**
     * Find transactions by sender ID (for P2P transfers)
     */
    Page<NFCTransaction> findBySenderIdOrderByCreatedAtDesc(String senderId, Pageable pageable);

    /**
     * Find transactions by recipient ID (for P2P transfers)
     */
    Page<NFCTransaction> findByRecipientIdOrderByCreatedAtDesc(String recipientId, Pageable pageable);

    /**
     * Find transactions by user ID (either as sender, recipient, customer, or merchant)
     */
    @Query("SELECT t FROM NFCTransaction t WHERE " +
           "t.customerId = :userId OR t.merchantId = :userId OR " +
           "t.senderId = :userId OR t.recipientId = :userId " +
           "ORDER BY t.createdAt DESC")
    Page<NFCTransaction> findByUserIdOrderByCreatedAtDesc(@Param("userId") String userId, Pageable pageable);

    /**
     * Find transactions by transaction type
     */
    Page<NFCTransaction> findByTransactionTypeOrderByCreatedAtDesc(String transactionType, Pageable pageable);

    /**
     * Find transactions by status
     */
    Page<NFCTransaction> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    /**
     * Find transactions by user ID and transaction type
     */
    @Query("SELECT t FROM NFCTransaction t WHERE " +
           "(t.customerId = :userId OR t.merchantId = :userId OR " +
           "t.senderId = :userId OR t.recipientId = :userId) " +
           "AND t.transactionType = :transactionType " +
           "ORDER BY t.createdAt DESC")
    Page<NFCTransaction> findByUserIdAndTransactionTypeOrderByCreatedAtDesc(
            @Param("userId") String userId, 
            @Param("transactionType") String transactionType, 
            Pageable pageable);

    /**
     * Find transactions by NFC session ID
     */
    List<NFCTransaction> findByNfcSessionIdOrderByCreatedAtDesc(String nfcSessionId);

    /**
     * Find transactions by device ID
     */
    Page<NFCTransaction> findByDeviceIdOrderByCreatedAtDesc(String deviceId, Pageable pageable);

    /**
     * Find transactions within date range
     */
    @Query("SELECT t FROM NFCTransaction t WHERE " +
           "t.createdAt >= :startDate AND t.createdAt <= :endDate " +
           "ORDER BY t.createdAt DESC")
    Page<NFCTransaction> findByDateRangeOrderByCreatedAtDesc(
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable);

    /**
     * Find pending transactions older than specified time
     */
    @Query("SELECT t FROM NFCTransaction t WHERE " +
           "t.status IN ('PENDING', 'PROCESSING') AND " +
           "t.createdAt < :cutoffTime")
    List<NFCTransaction> findPendingTransactionsOlderThan(@Param("cutoffTime") Instant cutoffTime);

    /**
     * Find failed transactions for retry
     */
    @Query("SELECT t FROM NFCTransaction t WHERE " +
           "t.status = 'FAILED' AND " +
           "t.errorCode IN :retryableErrors AND " +
           "t.createdAt > :minCreatedAt")
    List<NFCTransaction> findRetryableFailedTransactions(
            @Param("retryableErrors") List<String> retryableErrors,
            @Param("minCreatedAt") Instant minCreatedAt);

    /**
     * Get transaction statistics by user ID
     */
    @Query("SELECT " +
           "COUNT(t) as totalCount, " +
           "SUM(CASE WHEN t.status = 'SUCCESS' THEN 1 ELSE 0 END) as successCount, " +
           "SUM(CASE WHEN t.status = 'FAILED' THEN 1 ELSE 0 END) as failedCount, " +
           "SUM(CASE WHEN t.status = 'SUCCESS' THEN t.amount ELSE 0 END) as totalAmount " +
           "FROM NFCTransaction t WHERE " +
           "t.customerId = :userId OR t.merchantId = :userId OR " +
           "t.senderId = :userId OR t.recipientId = :userId")
    Object[] getTransactionStatsByUserId(@Param("userId") String userId);

    /**
     * Get daily transaction volume
     */
    @Query("SELECT " +
           "DATE(t.createdAt) as date, " +
           "COUNT(t) as transactionCount, " +
           "SUM(CASE WHEN t.status = 'SUCCESS' THEN t.amount ELSE 0 END) as totalAmount " +
           "FROM NFCTransaction t WHERE " +
           "t.createdAt >= :startDate AND t.createdAt <= :endDate " +
           "GROUP BY DATE(t.createdAt) " +
           "ORDER BY DATE(t.createdAt)")
    List<Object[]> getDailyTransactionVolume(
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);

    /**
     * Find transactions by fraud risk score range
     */
    @Query("SELECT t FROM NFCTransaction t WHERE " +
           "t.riskScore >= :minRiskScore AND t.riskScore <= :maxRiskScore " +
           "ORDER BY t.riskScore DESC, t.createdAt DESC")
    Page<NFCTransaction> findByRiskScoreRange(
            @Param("minRiskScore") BigDecimal minRiskScore,
            @Param("maxRiskScore") BigDecimal maxRiskScore,
            Pageable pageable);

    /**
     * Find high-risk transactions that passed fraud check
     */
    @Query("SELECT t FROM NFCTransaction t WHERE " +
           "t.riskScore > :riskThreshold AND " +
           "t.fraudCheckPassed = true AND " +
           "t.status = 'SUCCESS' " +
           "ORDER BY t.riskScore DESC, t.createdAt DESC")
    List<NFCTransaction> findHighRiskSuccessfulTransactions(@Param("riskThreshold") BigDecimal riskThreshold);

    /**
     * Count transactions by status for a user
     */
    @Query("SELECT t.status, COUNT(t) FROM NFCTransaction t WHERE " +
           "(t.customerId = :userId OR t.merchantId = :userId OR " +
           "t.senderId = :userId OR t.recipientId = :userId) " +
           "GROUP BY t.status")
    List<Object[]> countTransactionsByStatusForUser(@Param("userId") String userId);

    /**
     * Find transactions by location proximity
     */
    @Query("SELECT t FROM NFCTransaction t WHERE " +
           "t.latitude IS NOT NULL AND t.longitude IS NOT NULL AND " +
           "6371 * acos(cos(radians(:lat)) * cos(radians(t.latitude)) * " +
           "cos(radians(t.longitude) - radians(:lng)) + " +
           "sin(radians(:lat)) * sin(radians(t.latitude))) <= :radiusKm " +
           "ORDER BY t.createdAt DESC")
    Page<NFCTransaction> findByLocationProximity(
            @Param("lat") Double latitude,
            @Param("lng") Double longitude,
            @Param("radiusKm") Double radiusKm,
            Pageable pageable);

    /**
     * Get merchant transaction summary
     */
    @Query("SELECT " +
           "t.merchantId, " +
           "COUNT(t) as transactionCount, " +
           "SUM(CASE WHEN t.status = 'SUCCESS' THEN t.amount ELSE 0 END) as totalAmount, " +
           "AVG(t.processingTimeMs) as avgProcessingTime " +
           "FROM NFCTransaction t WHERE " +
           "t.merchantId = :merchantId AND " +
           "t.createdAt >= :startDate AND t.createdAt <= :endDate " +
           "GROUP BY t.merchantId")
    Object[] getMerchantTransactionSummary(
            @Param("merchantId") String merchantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);

    /**
     * Find duplicate transactions by device fingerprint and amount
     */
    @Query("SELECT t FROM NFCTransaction t WHERE " +
           "t.deviceFingerprint = :deviceFingerprint AND " +
           "t.amount = :amount AND " +
           "t.createdAt >= :timeWindow " +
           "ORDER BY t.createdAt DESC")
    List<NFCTransaction> findPotentialDuplicates(
            @Param("deviceFingerprint") String deviceFingerprint,
            @Param("amount") BigDecimal amount,
            @Param("timeWindow") Instant timeWindow);

    /**
     * Update transaction status
     */
    @Modifying
    @Query("UPDATE NFCTransaction t SET " +
           "t.status = :status, " +
           "t.updatedAt = :updatedAt, " +
           "t.completedAt = CASE WHEN :status IN ('SUCCESS', 'FAILED', 'CANCELLED') THEN :updatedAt ELSE t.completedAt END " +
           "WHERE t.transactionId = :transactionId")
    int updateTransactionStatus(
            @Param("transactionId") String transactionId,
            @Param("status") String status,
            @Param("updatedAt") Instant updatedAt);

    /**
     * Count active sessions for a device
     */
    @Query("SELECT COUNT(DISTINCT t.nfcSessionId) FROM NFCTransaction t WHERE " +
           "t.deviceId = :deviceId AND " +
           "t.status IN ('PENDING', 'PROCESSING') AND " +
           "t.createdAt > :since")
    Long countActiveSessionsForDevice(
            @Param("deviceId") String deviceId,
            @Param("since") Instant since);
}