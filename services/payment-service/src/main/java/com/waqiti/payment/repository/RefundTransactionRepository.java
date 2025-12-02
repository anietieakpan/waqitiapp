package com.waqiti.payment.repository;

import com.waqiti.payment.entity.RefundTransaction;
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

/**
 * Repository for RefundTransaction entities
 * 
 * Provides comprehensive data access methods for refund transaction
 * management with optimized queries for financial reconciliation.
 */
@Repository
public interface RefundTransactionRepository extends JpaRepository<RefundTransaction, java.util.UUID> {

    /**
     * Find refund by refund ID
     */
    Optional<RefundTransaction> findByRefundId(String refundId);

    /**
     * Find all refunds for a specific payment
     */
    List<RefundTransaction> findByOriginalPaymentIdOrderByCreatedAtDesc(String originalPaymentId);

    /**
     * Calculate total refunded amount for a payment (successful refunds only)
     * This is the key method for the TODO implementation
     */
    @Query("SELECT COALESCE(SUM(r.refundAmount), 0) FROM RefundTransaction r " +
           "WHERE r.originalPaymentId = :paymentId " +
           "AND r.status IN ('COMPLETED', 'PARTIAL_SUCCESS')")
    BigDecimal calculateTotalRefundedAmount(@Param("paymentId") String paymentId);

    /**
     * Calculate net refunded amount (after fees) for a payment
     */
    @Query("SELECT COALESCE(SUM(r.netRefundAmount), 0) FROM RefundTransaction r " +
           "WHERE r.originalPaymentId = :paymentId " +
           "AND r.status IN ('COMPLETED', 'PARTIAL_SUCCESS')")
    BigDecimal calculateNetRefundedAmount(@Param("paymentId") String paymentId);

    /**
     * Find refunds by status
     */
    List<RefundTransaction> findByStatusOrderByCreatedAtDesc(RefundTransaction.RefundStatus status);

    /**
     * Find refunds by provider
     */
    List<RefundTransaction> findByProviderTypeOrderByCreatedAtDesc(RefundTransaction.ProviderType providerType);

    /**
     * Find refunds by user
     */
    List<RefundTransaction> findByRequestedByOrderByCreatedAtDesc(String userId);

    /**
     * Find refunds requiring manual review
     */
    @Query("SELECT r FROM RefundTransaction r " +
           "WHERE r.status = 'REQUIRES_MANUAL_REVIEW' " +
           "OR r.complianceStatus = 'MANUAL_REVIEW' " +
           "ORDER BY r.createdAt ASC")
    List<RefundTransaction> findRefundsRequiringManualReview();

    /**
     * Find pending refunds older than specified time
     */
    @Query("SELECT r FROM RefundTransaction r " +
           "WHERE r.status IN ('PENDING', 'PROCESSING') " +
           "AND r.createdAt < :cutoffTime " +
           "ORDER BY r.createdAt ASC")
    List<RefundTransaction> findPendingRefundsOlderThan(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Find failed refunds that can be retried
     */
    @Query("SELECT r FROM RefundTransaction r " +
           "WHERE r.status = 'FAILED' " +
           "AND (r.retryCount IS NULL OR r.retryCount < 3) " +
           "AND (r.nextRetryAt IS NULL OR r.nextRetryAt <= :now) " +
           "ORDER BY r.createdAt ASC")
    List<RefundTransaction> findRetryableFailedRefunds(@Param("now") LocalDateTime now);

    /**
     * Find refunds by correlation ID for tracing
     */
    List<RefundTransaction> findByCorrelationIdOrderByCreatedAtDesc(String correlationId);

    /**
     * Find refunds by provider refund ID
     */
    Optional<RefundTransaction> findByProviderRefundId(String providerRefundId);

    /**
     * Find refunds by idempotency key
     */
    Optional<RefundTransaction> findByIdempotencyKey(String idempotencyKey);

    /**
     * Find refunds in date range
     */
    @Query("SELECT r FROM RefundTransaction r " +
           "WHERE r.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY r.createdAt DESC")
    Page<RefundTransaction> findRefundsInDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable);

    /**
     * Find unreconciled refunds
     */
    @Query("SELECT r FROM RefundTransaction r " +
           "WHERE r.status = 'COMPLETED' " +
           "AND (r.reconciliationStatus IS NULL OR r.reconciliationStatus = 'PENDING') " +
           "ORDER BY r.completedAt ASC")
    List<RefundTransaction> findUnreconciledRefunds();

    /**
     * Calculate refund statistics for a time period
     */
    @Query("SELECT " +
           "COUNT(r) as totalCount, " +
           "SUM(CASE WHEN r.status = 'COMPLETED' THEN 1 ELSE 0 END) as completedCount, " +
           "SUM(CASE WHEN r.status = 'FAILED' THEN 1 ELSE 0 END) as failedCount, " +
           "COALESCE(SUM(CASE WHEN r.status = 'COMPLETED' THEN r.refundAmount ELSE 0 END), 0) as totalRefundAmount " +
           "FROM RefundTransaction r " +
           "WHERE r.createdAt BETWEEN :startDate AND :endDate")
    RefundStatistics calculateRefundStatistics(@Param("startDate") LocalDateTime startDate,
                                             @Param("endDate") LocalDateTime endDate);

    /**
     * Find refunds by settlement batch
     */
    List<RefundTransaction> findBySettlementBatchIdOrderByCreatedAtDesc(String settlementBatchId);

    /**
     * Find high-value refunds above threshold
     */
    @Query("SELECT r FROM RefundTransaction r " +
           "WHERE r.refundAmount >= :threshold " +
           "AND r.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY r.refundAmount DESC")
    List<RefundTransaction> findHighValueRefunds(@Param("threshold") BigDecimal threshold,
                                               @Param("startDate") LocalDateTime startDate,
                                               @Param("endDate") LocalDateTime endDate);

    /**
     * Find refunds with compliance issues
     */
    @Query("SELECT r FROM RefundTransaction r " +
           "WHERE r.complianceStatus IN ('FAILED', 'MANUAL_REVIEW', 'WARNING') " +
           "ORDER BY r.createdAt DESC")
    List<RefundTransaction> findRefundsWithComplianceIssues();

    /**
     * Count refunds by status for dashboard
     */
    @Query("SELECT r.status, COUNT(r) FROM RefundTransaction r " +
           "WHERE r.createdAt >= :since " +
           "GROUP BY r.status")
    List<Object[]> countRefundsByStatusSince(@Param("since") LocalDateTime since);

    /**
     * DTO projection for refund statistics
     */
    interface RefundStatistics {
        Long getTotalCount();
        Long getCompletedCount();
        Long getFailedCount();
        BigDecimal getTotalRefundAmount();
    }
}