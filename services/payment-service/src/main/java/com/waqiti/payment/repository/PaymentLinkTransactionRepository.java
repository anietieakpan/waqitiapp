package com.waqiti.payment.repository;

import com.waqiti.payment.domain.PaymentLinkTransaction;
import com.waqiti.payment.domain.PaymentLinkTransaction.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 * Repository for PaymentLinkTransaction entities
 */
@Repository
public interface PaymentLinkTransactionRepository extends JpaRepository<PaymentLinkTransaction, UUID> {
    
    /**
     * Find transaction by transaction ID
     */
    Optional<PaymentLinkTransaction> findByTransactionId(String transactionId);
    
    /**
     * Find transactions for a specific payment link
     */
    Page<PaymentLinkTransaction> findByPaymentLinkIdOrderByCreatedAtDesc(UUID paymentLinkId, Pageable pageable);
    
    /**
     * Find transactions by payer
     */
    Page<PaymentLinkTransaction> findByPayerIdOrderByCreatedAtDesc(UUID payerId, Pageable pageable);
    
    /**
     * Find transactions by status
     */
    List<PaymentLinkTransaction> findByStatus(TransactionStatus status);
    
    /**
     * Find transactions for payment links created by a specific user
     */
    @Query("SELECT t FROM PaymentLinkTransaction t JOIN t.paymentLink pl " +
           "WHERE pl.creatorId = :creatorId ORDER BY t.createdAt DESC")
    Page<PaymentLinkTransaction> findTransactionsForCreator(@Param("creatorId") UUID creatorId, Pageable pageable);
    
    /**
     * Find completed transactions for a payment link
     */
    List<PaymentLinkTransaction> findByPaymentLinkIdAndStatus(UUID paymentLinkId, TransactionStatus status);
    
    /**
     * Get total amount collected for a payment link
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM PaymentLinkTransaction t " +
           "WHERE t.paymentLink.id = :paymentLinkId AND t.status = 'COMPLETED'")
    BigDecimal getTotalCollectedForPaymentLink(@Param("paymentLinkId") UUID paymentLinkId);
    
    /**
     * Count transactions for a payment link
     */
    long countByPaymentLinkIdAndStatus(UUID paymentLinkId, TransactionStatus status);
    
    /**
     * Find transactions in date range
     */
    @Query("SELECT t FROM PaymentLinkTransaction t WHERE t.createdAt BETWEEN :startDate AND :endDate")
    List<PaymentLinkTransaction> findTransactionsInDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find transactions by payment link and date range
     */
    @Query("SELECT t FROM PaymentLinkTransaction t WHERE t.paymentLink.id = :paymentLinkId " +
           "AND t.createdAt BETWEEN :startDate AND :endDate ORDER BY t.createdAt DESC")
    List<PaymentLinkTransaction> findByPaymentLinkAndDateRange(
            @Param("paymentLinkId") UUID paymentLinkId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find transactions by creator and date range
     */
    @Query("SELECT t FROM PaymentLinkTransaction t JOIN t.paymentLink pl " +
           "WHERE pl.creatorId = :creatorId AND t.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY t.createdAt DESC")
    Page<PaymentLinkTransaction> findByCreatorAndDateRange(
            @Param("creatorId") UUID creatorId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);
    
    /**
     * Find pending transactions older than specified time
     */
    @Query("SELECT t FROM PaymentLinkTransaction t WHERE t.status = 'PENDING' AND t.createdAt < :cutoffTime")
    List<PaymentLinkTransaction> findStalePendingTransactions(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    /**
     * Get creator statistics
     */
    @Query("SELECT COUNT(t), SUM(t.amount), AVG(t.amount) FROM PaymentLinkTransaction t " +
           "JOIN t.paymentLink pl WHERE pl.creatorId = :creatorId AND t.status = 'COMPLETED'")
    Object[] getCreatorTransactionStats(@Param("creatorId") UUID creatorId);
    
    /**
     * Find highest value transactions
     */
    @Query("SELECT t FROM PaymentLinkTransaction t WHERE t.status = 'COMPLETED' " +
           "ORDER BY t.amount DESC")
    Page<PaymentLinkTransaction> findHighestValueTransactions(Pageable pageable);
    
    /**
     * Find transactions by IP address (for fraud detection)
     */
    List<PaymentLinkTransaction> findByIpAddressOrderByCreatedAtDesc(String ipAddress);
    
    /**
     * Find transactions by email (for duplicate detection)
     */
    List<PaymentLinkTransaction> findByPayerEmailOrderByCreatedAtDesc(String payerEmail);
    
    /**
     * Count transactions from same IP in time window
     */
    @Query("SELECT COUNT(t) FROM PaymentLinkTransaction t WHERE t.ipAddress = :ipAddress " +
           "AND t.createdAt >= :timeWindow")
    long countRecentTransactionsByIp(@Param("ipAddress") String ipAddress, 
                                    @Param("timeWindow") LocalDateTime timeWindow);
    
    /**
     * Find recent failed transactions for monitoring
     */
    @Query("SELECT t FROM PaymentLinkTransaction t WHERE t.status = 'FAILED' " +
           "AND t.createdAt >= :recentTime ORDER BY t.createdAt DESC")
    List<PaymentLinkTransaction> findRecentFailedTransactions(@Param("recentTime") LocalDateTime recentTime);
}