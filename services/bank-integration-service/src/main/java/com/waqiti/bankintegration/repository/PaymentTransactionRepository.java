package com.waqiti.bankintegration.repository;

import com.waqiti.bankintegration.domain.PaymentTransaction;
import com.waqiti.bankintegration.domain.PaymentStatus;
import com.waqiti.bankintegration.domain.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for PaymentTransaction entity in bank integration service.
 * 
 * Provides comprehensive data access operations for payment transactions including
 * status tracking, audit trails, and financial reconciliation support.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    
    /**
     * Find payment transaction by transaction ID
     */
    Optional<PaymentTransaction> findByTransactionId(String transactionId);
    
    /**
     * Find payment transactions by user ID
     */
    List<PaymentTransaction> findByUserId(UUID userId);
    
    /**
     * Find payment transactions by bank account ID
     */
    List<PaymentTransaction> findByBankAccountId(UUID bankAccountId);
    
    /**
     * Find payment transaction by provider transaction ID
     */
    Optional<PaymentTransaction> findByProviderTransactionId(String providerTransactionId);
    
    /**
     * Find payment transactions by status
     */
    List<PaymentTransaction> findByStatus(PaymentStatus status);
    
    /**
     * Find payment transactions by payment type
     */
    List<PaymentTransaction> findByPaymentType(PaymentType paymentType);
    
    /**
     * Find payment transactions by user and status
     */
    List<PaymentTransaction> findByUserIdAndStatus(UUID userId, PaymentStatus status);
    
    /**
     * Find payment transactions by user within date range
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.userId = :userId " +
           "AND pt.createdAt BETWEEN :startDate AND :endDate ORDER BY pt.createdAt DESC")
    List<PaymentTransaction> findByUserIdAndDateRange(@Param("userId") UUID userId,
                                                     @Param("startDate") LocalDateTime startDate,
                                                     @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find processing transactions that are stale (older than threshold)
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.status = 'PROCESSING' " +
           "AND pt.createdAt < :thresholdTime")
    List<PaymentTransaction> findStaleProcessingTransactions(@Param("thresholdTime") LocalDateTime thresholdTime);
    
    /**
     * Find transactions requiring completion check
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.status = 'PROCESSING' " +
           "AND pt.expectedCompletionTime <= :currentTime")
    List<PaymentTransaction> findTransactionsRequiringCompletionCheck(@Param("currentTime") LocalDateTime currentTime);
    
    /**
     * Find failed transactions for retry analysis
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.status = 'FAILED' " +
           "AND pt.createdAt BETWEEN :startDate AND :endDate")
    List<PaymentTransaction> findFailedTransactionsInPeriod(@Param("startDate") LocalDateTime startDate,
                                                           @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find refund transactions by original transaction ID
     */
    List<PaymentTransaction> findByOriginalTransactionId(String originalTransactionId);
    
    /**
     * Calculate total amount by user and status within date range
     */
    @Query("SELECT COALESCE(SUM(pt.amount), 0) FROM PaymentTransaction pt " +
           "WHERE pt.userId = :userId AND pt.status = :status " +
           "AND pt.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal calculateTotalAmountByUserAndStatus(@Param("userId") UUID userId,
                                                  @Param("status") PaymentStatus status,
                                                  @Param("startDate") LocalDateTime startDate,
                                                  @Param("endDate") LocalDateTime endDate);
    
    /**
     * Count transactions by status for monitoring
     */
    long countByStatus(PaymentStatus status);
    
    /**
     * Count user transactions within date range
     */
    @Query("SELECT COUNT(pt) FROM PaymentTransaction pt WHERE pt.userId = :userId " +
           "AND pt.createdAt BETWEEN :startDate AND :endDate")
    long countUserTransactionsInPeriod(@Param("userId") UUID userId,
                                      @Param("startDate") LocalDateTime startDate,
                                      @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find transactions requiring reconciliation
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.status = 'COMPLETED' " +
           "AND pt.reconciledAt IS NULL AND pt.completedAt <= :cutoffTime")
    List<PaymentTransaction> findTransactionsRequiringReconciliation(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    /**
     * Find duplicate transactions by amount and user within time window
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.userId = :userId " +
           "AND pt.amount = :amount AND pt.currency = :currency " +
           "AND pt.createdAt BETWEEN :startTime AND :endTime " +
           "AND pt.status != 'FAILED' ORDER BY pt.createdAt")
    List<PaymentTransaction> findPotentialDuplicateTransactions(@Param("userId") UUID userId,
                                                               @Param("amount") BigDecimal amount,
                                                               @Param("currency") String currency,
                                                               @Param("startTime") LocalDateTime startTime,
                                                               @Param("endTime") LocalDateTime endTime);
    
    /**
     * Find high-value transactions for enhanced monitoring
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.amount >= :threshold " +
           "AND pt.createdAt BETWEEN :startDate AND :endDate ORDER BY pt.amount DESC")
    List<PaymentTransaction> findHighValueTransactions(@Param("threshold") BigDecimal threshold,
                                                      @Param("startDate") LocalDateTime startDate,
                                                      @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find transactions by error pattern for analysis
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.status = 'FAILED' " +
           "AND pt.errorMessage LIKE :errorPattern " +
           "AND pt.createdAt BETWEEN :startDate AND :endDate")
    List<PaymentTransaction> findTransactionsByErrorPattern(@Param("errorPattern") String errorPattern,
                                                           @Param("startDate") LocalDateTime startDate,
                                                           @Param("endDate") LocalDateTime endDate);
    
    /**
     * Check if transaction exists within time window (for idempotency)
     */
    @Query("SELECT COUNT(pt) > 0 FROM PaymentTransaction pt WHERE pt.userId = :userId " +
           "AND pt.amount = :amount AND pt.currency = :currency " +
           "AND pt.description = :description AND pt.paymentType = :paymentType " +
           "AND pt.createdAt >= :timeWindow AND pt.status != 'FAILED'")
    boolean existsSimilarRecentTransaction(@Param("userId") UUID userId,
                                         @Param("amount") BigDecimal amount,
                                         @Param("currency") String currency,
                                         @Param("description") String description,
                                         @Param("paymentType") PaymentType paymentType,
                                         @Param("timeWindow") LocalDateTime timeWindow);
    
    /**
     * Find user's recent successful transactions for pattern analysis
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.userId = :userId " +
           "AND pt.status = 'COMPLETED' AND pt.completedAt >= :sinceDate " +
           "ORDER BY pt.completedAt DESC")
    List<PaymentTransaction> findRecentSuccessfulTransactionsByUser(@Param("userId") UUID userId,
                                                                   @Param("sinceDate") LocalDateTime sinceDate);
    
    /**
     * Find transactions requiring provider status sync
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.status IN ('PROCESSING', 'PENDING') " +
           "AND pt.lastStatusCheckAt IS NULL OR pt.lastStatusCheckAt < :lastCheckThreshold")
    List<PaymentTransaction> findTransactionsRequiringStatusSync(@Param("lastCheckThreshold") LocalDateTime lastCheckThreshold);
}