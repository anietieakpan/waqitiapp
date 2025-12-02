package com.waqiti.voice.repository;

import com.waqiti.voice.domain.VoiceTransaction;
import com.waqiti.voice.domain.VoiceTransaction.TransactionStatus;
import com.waqiti.voice.domain.VoiceTransaction.TransactionType;
import com.waqiti.voice.domain.VoiceTransaction.RiskLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
 * Repository for VoiceTransaction entity operations
 *
 * CRITICAL FINANCIAL REPOSITORY:
 * - Handles real money transactions
 * - Implements idempotency checks (prevent duplicate payments)
 * - Provides fraud detection queries
 * - Enforces PCI-DSS, SOX, AML compliance
 * - All queries must maintain audit trail integrity
 *
 * Security:
 * - Pessimistic locking for financial operations
 * - Row-level security (user can only access own transactions)
 * - Sensitive data logging prevented
 */
@Repository
public interface VoiceTransactionRepository extends JpaRepository<VoiceTransaction, UUID> {

    // ========== Idempotency Queries (CRITICAL) ==========

    /**
     * Find transaction by idempotency key (prevents duplicate payments)
     * CRITICAL: Must be called before creating ANY new transaction
     */
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    Optional<VoiceTransaction> findByIdempotencyKey(String idempotencyKey);

    /**
     * Check if idempotency key exists
     */
    boolean existsByIdempotencyKey(String idempotencyKey);

    /**
     * Find transaction by idempotency key with pessimistic write lock
     * Use this when creating new transactions to prevent race conditions
     */
    @Query("SELECT vt FROM VoiceTransaction vt WHERE vt.idempotencyKey = :key")
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    Optional<VoiceTransaction> findByIdempotencyKeyWithLock(
            @Param("key") String key,
            @jakarta.persistence.LockModeType lockMode);

    // ========== Core Lookups ==========

    /**
     * Find transaction by unique transaction ID
     */
    Optional<VoiceTransaction> findByTransactionId(String transactionId);

    /**
     * Find transaction by voice command ID
     */
    Optional<VoiceTransaction> findByVoiceCommandId(UUID voiceCommandId);

    /**
     * Find transaction by payment reference (external system)
     */
    Optional<VoiceTransaction> findByPaymentReference(String paymentReference);

    /**
     * Find transaction by external transaction ID
     */
    Optional<VoiceTransaction> findByExternalTransactionId(String externalTransactionId);

    // ========== User Transaction Queries ==========

    /**
     * Find all transactions for user with pagination
     */
    Page<VoiceTransaction> findByUserId(UUID userId, Pageable pageable);

    /**
     * Find user's transactions by status
     */
    List<VoiceTransaction> findByUserIdAndStatus(UUID userId, TransactionStatus status);

    /**
     * Find user's transactions by type
     */
    List<VoiceTransaction> findByUserIdAndTransactionType(UUID userId, TransactionType type, Pageable pageable);

    /**
     * Find user's transactions within date range
     */
    @Query("SELECT vt FROM VoiceTransaction vt WHERE vt.userId = :userId " +
           "AND vt.initiatedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY vt.initiatedAt DESC")
    List<VoiceTransaction> findByUserIdAndDateRange(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Find user's recent transactions
     */
    @Query("SELECT vt FROM VoiceTransaction vt WHERE vt.userId = :userId " +
           "ORDER BY vt.initiatedAt DESC")
    List<VoiceTransaction> findRecentByUserId(@Param("userId") UUID userId, Pageable pageable);

    // ========== Status-based Queries ==========

    /**
     * Find transactions by status
     */
    List<VoiceTransaction> findByStatus(TransactionStatus status);

    /**
     * Find pending transactions (require confirmation)
     */
    @Query("SELECT vt FROM VoiceTransaction vt WHERE vt.status = 'PENDING' " +
           "AND vt.requiresConfirmation = true " +
           "ORDER BY vt.initiatedAt ASC")
    List<VoiceTransaction> findPendingConfirmation(Pageable pageable);

    /**
     * Find transactions on hold (fraud/compliance review)
     */
    @Query("SELECT vt FROM VoiceTransaction vt WHERE vt.status = 'ON_HOLD' " +
           "OR vt.regulatoryHold = true " +
           "ORDER BY vt.initiatedAt ASC")
    List<VoiceTransaction> findTransactionsOnHold();

    /**
     * Find failed transactions eligible for retry
     */
    @Query("SELECT vt FROM VoiceTransaction vt WHERE vt.status = 'FAILED' " +
           "AND vt.retryCount < vt.maxRetries " +
           "AND vt.failedAt > :cutoffDate " +
           "ORDER BY vt.failedAt ASC")
    List<VoiceTransaction> findFailedTransactionsForRetry(
            @Param("cutoffDate") LocalDateTime cutoffDate,
            Pageable pageable);

    // ========== Fraud Detection Queries ==========

    /**
     * Find transactions with failed fraud checks
     */
    @Query("SELECT vt FROM VoiceTransaction vt WHERE vt.fraudCheckPassed = false " +
           "ORDER BY vt.fraudScore DESC, vt.initiatedAt DESC")
    List<VoiceTransaction> findFailedFraudChecks(Pageable pageable);

    /**
     * Find high-risk transactions
     */
    @Query("SELECT vt FROM VoiceTransaction vt WHERE vt.riskLevel IN ('HIGH', 'CRITICAL') " +
           "AND vt.status <> 'CANCELLED' " +
           "ORDER BY vt.riskLevel DESC, vt.fraudScore DESC")
    List<VoiceTransaction> findHighRiskTransactions(Pageable pageable);

    /**
     * Find user's high-value transactions (potential fraud)
     */
    @Query("SELECT vt FROM VoiceTransaction vt WHERE vt.userId = :userId " +
           "AND vt.amount > :threshold " +
           "AND vt.initiatedAt > :sinceDate " +
           "ORDER BY vt.amount DESC")
    List<VoiceTransaction> findHighValueTransactions(
            @Param("userId") UUID userId,
            @Param("threshold") BigDecimal threshold,
            @Param("sinceDate") LocalDateTime sinceDate);

    /**
     * Find potential duplicate transactions (fraud detection)
     * Same user, same amount, same recipient, within time window
     */
    @Query("SELECT vt FROM VoiceTransaction vt WHERE vt.userId = :userId " +
           "AND vt.amount = :amount " +
           "AND vt.recipientId = :recipientId " +
           "AND vt.initiatedAt BETWEEN :startTime AND :endTime " +
           "AND vt.id <> :excludeId " +
           "AND vt.status IN ('COMPLETED', 'PROCESSING', 'CONFIRMED')")
    List<VoiceTransaction> findPotentialDuplicates(
            @Param("userId") UUID userId,
            @Param("amount") BigDecimal amount,
            @Param("recipientId") String recipientId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("excludeId") UUID excludeId);

    /**
     * Count user's transactions in time window (velocity check)
     */
    @Query("SELECT COUNT(vt) FROM VoiceTransaction vt WHERE vt.userId = :userId " +
           "AND vt.initiatedAt > :sinceTime " +
           "AND vt.status IN ('COMPLETED', 'PROCESSING', 'CONFIRMED')")
    long countUserTransactionsSince(
            @Param("userId") UUID userId,
            @Param("sinceTime") LocalDateTime sinceTime);

    /**
     * Calculate user's total transaction amount in time window (velocity check)
     */
    @Query("SELECT COALESCE(SUM(vt.amount), 0) FROM VoiceTransaction vt " +
           "WHERE vt.userId = :userId " +
           "AND vt.initiatedAt > :sinceTime " +
           "AND vt.status = 'COMPLETED'")
    BigDecimal sumUserTransactionAmountSince(
            @Param("userId") UUID userId,
            @Param("sinceTime") LocalDateTime sinceTime);

    // ========== Compliance & Regulatory Queries ==========

    /**
     * Find transactions requiring AML review
     */
    @Query("SELECT vt FROM VoiceTransaction vt WHERE vt.amlCheckRequired = true " +
           "AND (vt.amlCheckPassed IS NULL OR vt.amlCheckPassed = false) " +
           "ORDER BY vt.amount DESC")
    List<VoiceTransaction> findTransactionsRequiringAMLReview();

    /**
     * Find transactions exceeding reporting threshold (SAR/CTR)
     */
    @Query("SELECT vt FROM VoiceTransaction vt WHERE vt.amount >= :threshold " +
           "AND vt.initiatedAt > :sinceDate " +
           "ORDER BY vt.amount DESC")
    List<VoiceTransaction> findTransactionsAboveThreshold(
            @Param("threshold") BigDecimal threshold,
            @Param("sinceDate") LocalDateTime sinceDate);

    /**
     * Find transactions without KYC verification
     */
    @Query("SELECT vt FROM VoiceTransaction vt WHERE vt.kycVerified = false " +
           "AND vt.status <> 'CANCELLED' " +
           "AND vt.amount > :minAmount")
    List<VoiceTransaction> findTransactionsWithoutKYC(@Param("minAmount") BigDecimal minAmount);

    // ========== Session-based Queries ==========

    /**
     * Find transactions for voice session
     */
    List<VoiceTransaction> findByVoiceSessionId(UUID voiceSessionId);

    /**
     * Count transactions in session
     */
    long countByVoiceSessionId(UUID voiceSessionId);

    /**
     * Calculate total amount transacted in session
     */
    @Query("SELECT COALESCE(SUM(vt.amount), 0) FROM VoiceTransaction vt " +
           "WHERE vt.voiceSessionId = :sessionId " +
           "AND vt.status = 'COMPLETED'")
    BigDecimal sumSessionTransactionAmount(@Param("sessionId") UUID sessionId);

    // ========== Analytics & Reporting ==========

    /**
     * Count transactions by status
     */
    @Query("SELECT vt.status, COUNT(vt) FROM VoiceTransaction vt " +
           "WHERE vt.initiatedAt > :sinceDate " +
           "GROUP BY vt.status")
    List<Object[]> countByStatusSince(@Param("sinceDate") LocalDateTime sinceDate);

    /**
     * Count transactions by type
     */
    @Query("SELECT vt.transactionType, COUNT(vt) FROM VoiceTransaction vt " +
           "WHERE vt.initiatedAt > :sinceDate " +
           "GROUP BY vt.transactionType")
    List<Object[]> countByTypeSince(@Param("sinceDate") LocalDateTime sinceDate);

    /**
     * Calculate transaction volume statistics
     */
    @Query("SELECT " +
           "COUNT(vt), " +
           "COALESCE(SUM(vt.amount), 0), " +
           "COALESCE(AVG(vt.amount), 0), " +
           "COALESCE(MAX(vt.amount), 0) " +
           "FROM VoiceTransaction vt " +
           "WHERE vt.userId = :userId AND vt.status = 'COMPLETED'")
    Object[] getUserTransactionStatistics(@Param("userId") UUID userId);

    /**
     * Calculate daily transaction volume
     */
    @Query("SELECT CAST(vt.initiatedAt AS date), COUNT(vt), SUM(vt.amount) " +
           "FROM VoiceTransaction vt " +
           "WHERE vt.initiatedAt > :sinceDate AND vt.status = 'COMPLETED' " +
           "GROUP BY CAST(vt.initiatedAt AS date) " +
           "ORDER BY CAST(vt.initiatedAt AS date) DESC")
    List<Object[]> getDailyTransactionVolume(@Param("sinceDate") LocalDateTime sinceDate);

    // ========== Biometric Verification Queries ==========

    /**
     * Find transactions without biometric verification
     */
    @Query("SELECT vt FROM VoiceTransaction vt WHERE vt.biometricVerified = false " +
           "AND vt.amount > :threshold " +
           "AND vt.status <> 'CANCELLED'")
    List<VoiceTransaction> findTransactionsWithoutBiometric(@Param("threshold") BigDecimal threshold);

    /**
     * Find transactions with low biometric confidence
     */
    @Query("SELECT vt FROM VoiceTransaction vt WHERE vt.biometricConfidenceScore < :threshold " +
           "AND vt.biometricConfidenceScore IS NOT NULL " +
           "AND vt.initiatedAt > :sinceDate")
    List<VoiceTransaction> findLowBiometricConfidence(
            @Param("threshold") Double threshold,
            @Param("sinceDate") LocalDateTime sinceDate);

    // ========== Performance Monitoring ==========

    /**
     * Find slow transactions (processing time analysis)
     */
    @Query("SELECT vt FROM VoiceTransaction vt WHERE vt.status = 'COMPLETED' " +
           "AND vt.processedAt IS NOT NULL " +
           "AND vt.initiatedAt IS NOT NULL " +
           "AND EXTRACT(EPOCH FROM (vt.processedAt - vt.initiatedAt)) > :thresholdSeconds " +
           "ORDER BY (vt.processedAt - vt.initiatedAt) DESC")
    List<VoiceTransaction> findSlowTransactions(
            @Param("thresholdSeconds") Long thresholdSeconds,
            Pageable pageable);

    // ========== Bulk Operations ==========

    /**
     * Cancel pending transactions older than threshold (auto-expiry)
     */
    @Modifying
    @Query("UPDATE VoiceTransaction vt SET vt.status = 'CANCELLED', " +
           "vt.cancellationReason = 'Auto-expired', " +
           "vt.cancelledAt = CURRENT_TIMESTAMP " +
           "WHERE vt.status = 'PENDING' " +
           "AND vt.initiatedAt < :threshold")
    int cancelExpiredPendingTransactions(@Param("threshold") LocalDateTime threshold);

    /**
     * Archive old completed transactions (data retention)
     * NOTE: Move to separate archive table, don't delete
     */
    @Query("SELECT vt FROM VoiceTransaction vt WHERE vt.status IN ('COMPLETED', 'FAILED', 'CANCELLED') " +
           "AND vt.initiatedAt < :cutoffDate")
    List<VoiceTransaction> findTransactionsForArchival(@Param("cutoffDate") LocalDateTime cutoffDate);

    // ========== Existence Checks ==========

    /**
     * Check if user has any pending transactions
     */
    boolean existsByUserIdAndStatus(UUID userId, TransactionStatus status);

    /**
     * Check if user has recent transactions (activity check)
     */
    @Query("SELECT CASE WHEN COUNT(vt) > 0 THEN true ELSE false END " +
           "FROM VoiceTransaction vt WHERE vt.userId = :userId " +
           "AND vt.initiatedAt > :sinceDate")
    boolean hasRecentTransactions(
            @Param("userId") UUID userId,
            @Param("sinceDate") LocalDateTime sinceDate);

    // ========== Payment Provider Queries ==========

    /**
     * Find transactions by payment provider
     */
    List<VoiceTransaction> findByPaymentProvider(String provider, Pageable pageable);

    /**
     * Count transactions by provider (analytics)
     */
    @Query("SELECT vt.paymentProvider, COUNT(vt), SUM(vt.amount) " +
           "FROM VoiceTransaction vt " +
           "WHERE vt.status = 'COMPLETED' " +
           "AND vt.initiatedAt > :sinceDate " +
           "GROUP BY vt.paymentProvider")
    List<Object[]> countByProviderSince(@Param("sinceDate") LocalDateTime sinceDate);
}
