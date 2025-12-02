package com.waqiti.payment.repository;

import com.waqiti.payment.entity.PaymentTransaction;
import com.waqiti.payment.commons.domain.PaymentStatus;
import com.waqiti.payment.entity.PaymentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for payment transaction management with comprehensive query support
 */
@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID>, 
                                             JpaSpecificationExecutor<PaymentTransaction> {

    /**
     * Find transaction by transaction ID
     */
    Optional<PaymentTransaction> findByTransactionId(String transactionId);
    
    /**
     * Check if transaction ID exists
     */
    boolean existsByTransactionId(String transactionId);
    
    /**
     * Find transactions by payer ID
     */
    Page<PaymentTransaction> findByPayerIdOrderByCreatedAtDesc(UUID payerId, Pageable pageable);
    
    /**
     * Find transactions by payee ID
     */
    Page<PaymentTransaction> findByPayeeIdOrderByCreatedAtDesc(UUID payeeId, Pageable pageable);
    
    /**
     * Find transactions by status
     */
    Page<PaymentTransaction> findByStatusOrderByCreatedAtDesc(PaymentStatus status, Pageable pageable);
    
    /**
     * Find transactions by payment type
     */
    Page<PaymentTransaction> findByPaymentTypeOrderByCreatedAtDesc(PaymentType paymentType, Pageable pageable);
    
    /**
     * Find transactions by payer and status
     */
    List<PaymentTransaction> findByPayerIdAndStatus(UUID payerId, PaymentStatus status);
    
    /**
     * Find transactions by payee and status
     */
    List<PaymentTransaction> findByPayeeIdAndStatus(UUID payeeId, PaymentStatus status);
    
    /**
     * Find transactions within date range
     */
    @Query("SELECT t FROM PaymentTransaction t WHERE " +
           "t.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY t.createdAt DESC")
    Page<PaymentTransaction> findByDateRange(@Param("startDate") LocalDateTime startDate,
                                            @Param("endDate") LocalDateTime endDate,
                                            Pageable pageable);
    
    /**
     * Find transactions by amount range
     */
    @Query("SELECT t FROM PaymentTransaction t WHERE " +
           "t.amount BETWEEN :minAmount AND :maxAmount " +
           "ORDER BY t.amount DESC")
    Page<PaymentTransaction> findByAmountRange(@Param("minAmount") BigDecimal minAmount,
                                              @Param("maxAmount") BigDecimal maxAmount,
                                              Pageable pageable);
    
    /**
     * Calculate total transaction volume for a user
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM PaymentTransaction t " +
           "WHERE (t.payerId = :userId OR t.payeeId = :userId) " +
           "AND t.status = 'COMPLETED' " +
           "AND t.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal calculateUserTransactionVolume(@Param("userId") UUID userId,
                                             @Param("startDate") LocalDateTime startDate,
                                             @Param("endDate") LocalDateTime endDate);
    
    /**
     * Count transactions for velocity checking
     */
    @Query("SELECT COUNT(t) FROM PaymentTransaction t " +
           "WHERE t.payerId = :payerId " +
           "AND t.createdAt > :sinceTime")
    long countTransactionsSince(@Param("payerId") UUID payerId,
                               @Param("sinceTime") LocalDateTime sinceTime);
    
    /**
     * Find pending transactions requiring processing
     */
    @Query("SELECT t FROM PaymentTransaction t WHERE " +
           "t.status IN ('INITIATED', 'AUTHORIZED') " +
           "AND t.createdAt < :cutoffTime")
    List<PaymentTransaction> findPendingTransactions(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    /**
     * Find failed transactions for retry
     */
    @Query("SELECT t FROM PaymentTransaction t WHERE " +
           "t.status IN ('FAILED', 'SETTLEMENT_FAILED') " +
           "AND t.retryCount < :maxRetries " +
           "AND t.createdAt > :minCreatedAt")
    List<PaymentTransaction> findRetryableTransactions(@Param("maxRetries") Integer maxRetries,
                                                      @Param("minCreatedAt") LocalDateTime minCreatedAt);
    
    /**
     * Find transactions with high fraud scores
     */
    @Query("SELECT t FROM PaymentTransaction t WHERE " +
           "t.fraudScore > :threshold " +
           "ORDER BY t.fraudScore DESC")
    List<PaymentTransaction> findHighRiskTransactions(@Param("threshold") BigDecimal threshold);
    
    /**
     * Update transaction status
     */
    @Modifying
    @Transactional
    @Query("UPDATE PaymentTransaction t SET " +
           "t.status = :newStatus, " +
           "t.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE t.id = :transactionId AND t.status = :currentStatus")
    int updateStatus(@Param("transactionId") UUID transactionId,
                    @Param("currentStatus") PaymentStatus currentStatus,
                    @Param("newStatus") PaymentStatus newStatus);
    
    /**
     * Mark transaction as settled
     */
    @Modifying
    @Transactional
    @Query("UPDATE PaymentTransaction t SET " +
           "t.status = 'COMPLETED', " +
           "t.settlementId = :settlementId, " +
           "t.settledAt = :settledAt, " +
           "t.actualSettlementAmount = :settlementAmount " +
           "WHERE t.id = :transactionId")
    int markAsSettled(@Param("transactionId") UUID transactionId,
                     @Param("settlementId") String settlementId,
                     @Param("settledAt") Instant settledAt,
                     @Param("settlementAmount") BigDecimal settlementAmount);
    
    /**
     * Increment retry count
     */
    @Modifying
    @Transactional
    @Query("UPDATE PaymentTransaction t SET " +
           "t.retryCount = COALESCE(t.retryCount, 0) + 1, " +
           "t.lastRetryAt = CURRENT_TIMESTAMP " +
           "WHERE t.id = :transactionId")
    int incrementRetryCount(@Param("transactionId") UUID transactionId);
    
    /**
     * Get transaction statistics
     */
    @Query("SELECT NEW com.waqiti.payment.dto.TransactionStatistics(" +
           "COUNT(t), " +
           "SUM(CASE WHEN t.status = 'COMPLETED' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN t.status = 'FAILED' THEN 1 ELSE 0 END), " +
           "COALESCE(SUM(t.amount), 0), " +
           "COALESCE(AVG(t.amount), 0), " +
           "COALESCE(AVG(t.processingTimeMs), 0)) " +
           "FROM PaymentTransaction t " +
           "WHERE t.createdAt BETWEEN :startDate AND :endDate")
    Object getStatistics(@Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find duplicate transactions for fraud detection
     */
    @Query("SELECT t FROM PaymentTransaction t WHERE " +
           "t.amount = :amount " +
           "AND t.payerId = :payerId " +
           "AND t.payeeId = :payeeId " +
           "AND t.createdAt BETWEEN :startTime AND :endTime " +
           "AND t.status != 'FAILED'")
    List<PaymentTransaction> findPotentialDuplicates(@Param("amount") BigDecimal amount,
                                                    @Param("payerId") UUID payerId,
                                                    @Param("payeeId") UUID payeeId,
                                                    @Param("startTime") LocalDateTime startTime,
                                                    @Param("endTime") LocalDateTime endTime);
    
    /**
     * Get daily transaction summary
     */
    @Query("SELECT DATE(t.createdAt) as date, " +
           "COUNT(t) as count, " +
           "SUM(t.amount) as totalAmount, " +
           "SUM(t.processingFee) as totalFees " +
           "FROM PaymentTransaction t " +
           "WHERE t.status = 'COMPLETED' " +
           "AND t.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY DATE(t.createdAt) " +
           "ORDER BY DATE(t.createdAt)")
    List<Object[]> getDailyTransactionSummary(@Param("startDate") LocalDateTime startDate,
                                             @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find transactions requiring AML review
     */
    @Query("SELECT t FROM PaymentTransaction t WHERE " +
           "t.status = 'AML_REVIEW' " +
           "ORDER BY t.createdAt")
    List<PaymentTransaction> findTransactionsRequiringAMLReview();
    
    /**
     * Find transactions by reservation ID
     */
    Optional<PaymentTransaction> findByReservationId(String reservationId);
    
    /**
     * Clean up old completed transactions
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM PaymentTransaction t WHERE " +
           "t.status = 'COMPLETED' " +
           "AND t.createdAt < :cutoffDate")
    int deleteOldCompletedTransactions(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    // ===============================================
    // N+1 QUERY OPTIMIZATION METHODS - PAYMENT PERFORMANCE
    // ===============================================
    
    /**
     * N+1 QUERY FIX: Get payment summaries without lazy loading - projection query
     * Returns only essential data for payment lists, avoiding entity graph overhead
     */
    @Query("SELECT t.id, t.transactionId, t.payerId, t.payeeId, t.amount, " +
           "t.currency, t.status, t.paymentType, t.createdAt, t.updatedAt " +
           "FROM PaymentTransaction t " +
           "WHERE t.payerId IN :userIds OR t.payeeId IN :userIds " +
           "ORDER BY t.createdAt DESC")
    List<Object[]> findPaymentSummariesByUserIds(@Param("userIds") List<UUID> userIds,
                                                 org.springframework.data.domain.Pageable pageable);
    
    /**
     * N+1 QUERY FIX: Bulk update payment statuses in single transaction
     * Critical for batch payment processing operations
     */
    @Modifying
    @Transactional
    @Query("UPDATE PaymentTransaction t SET " +
           "t.status = :newStatus, " +
           "t.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE t.id IN :transactionIds AND t.status = :currentStatus")
    int bulkUpdatePaymentStatus(@Param("transactionIds") List<UUID> transactionIds,
                               @Param("currentStatus") PaymentStatus currentStatus,
                               @Param("newStatus") PaymentStatus newStatus);
    
    /**
     * N+1 QUERY FIX: Get user payment statistics in single aggregation query
     * Optimized for user dashboard analytics without loading full entities
     */
    @Query("SELECT " +
           "CASE WHEN t.payerId = :userId THEN 'OUTGOING' ELSE 'INCOMING' END as direction, " +
           "COUNT(t) as transactionCount, " +
           "SUM(t.amount) as totalAmount, " +
           "AVG(t.amount) as averageAmount, " +
           "MAX(t.amount) as maxAmount, " +
           "MIN(t.amount) as minAmount " +
           "FROM PaymentTransaction t " +
           "WHERE (t.payerId = :userId OR t.payeeId = :userId) " +
           "AND t.status = 'COMPLETED' " +
           "AND t.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY CASE WHEN t.payerId = :userId THEN 'OUTGOING' ELSE 'INCOMING' END")
    List<Object[]> getUserPaymentStatistics(@Param("userId") UUID userId,
                                           @Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate);
}