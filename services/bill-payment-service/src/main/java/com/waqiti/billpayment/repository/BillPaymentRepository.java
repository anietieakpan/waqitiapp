package com.waqiti.billpayment.repository;

import com.waqiti.billpayment.entity.BillPayment;
import com.waqiti.billpayment.entity.BillPaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for BillPayment entity operations
 */
@Repository
public interface BillPaymentRepository extends JpaRepository<BillPayment, UUID> {

    /**
     * Find all payments for a specific user
     */
    Page<BillPayment> findByUserId(String userId, Pageable pageable);

    /**
     * Find all payments for a specific bill
     */
    List<BillPayment> findByBillId(UUID billId);

    /**
     * Find payments by status
     */
    List<BillPayment> findByStatus(BillPaymentStatus status);

    /**
     * Find payments by user and status
     */
    Page<BillPayment> findByUserIdAndStatus(String userId, BillPaymentStatus status, Pageable pageable);

    /**
     * Find scheduled payments that need to be processed
     */
    @Query("SELECT bp FROM BillPayment bp WHERE bp.status = 'SCHEDULED' AND bp.scheduledDate <= :now")
    List<BillPayment> findScheduledPaymentsDueNow(@Param("now") LocalDateTime now);

    /**
     * Find failed payments eligible for retry
     */
    @Query("SELECT bp FROM BillPayment bp WHERE bp.status = 'FAILED' " +
           "AND bp.retryCount < bp.maxRetries " +
           "AND bp.nextRetryAt <= :now")
    List<BillPayment> findFailedPaymentsForRetry(@Param("now") LocalDateTime now);

    /**
     * Find payment by idempotency key
     */
    Optional<BillPayment> findByIdempotencyKey(String idempotencyKey);

    /**
     * Find payment by external payment ID
     */
    Optional<BillPayment> findByExternalPaymentId(String externalPaymentId);

    /**
     * Get payment statistics for a user
     */
    @Query("SELECT COUNT(bp), SUM(bp.amount), AVG(bp.amount) " +
           "FROM BillPayment bp " +
           "WHERE bp.userId = :userId AND bp.status = 'COMPLETED' " +
           "AND bp.completedAt BETWEEN :startDate AND :endDate")
    Object[] getPaymentStatistics(@Param("userId") String userId,
                                   @Param("startDate") LocalDateTime startDate,
                                   @Param("endDate") LocalDateTime endDate);

    /**
     * Find payments created between dates
     */
    List<BillPayment> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Count pending payments for user
     */
    long countByUserIdAndStatus(String userId, BillPaymentStatus status);

    /**
     * Find recent payments by user
     */
    @Query("SELECT bp FROM BillPayment bp WHERE bp.userId = :userId ORDER BY bp.createdAt DESC")
    List<BillPayment> findRecentPaymentsByUser(@Param("userId") String userId, Pageable pageable);

    /**
     * Check if bill has any successful payments
     */
    boolean existsByBillIdAndStatus(UUID billId, BillPaymentStatus status);

    /**
     * Find all processing payments (potential stuck payments)
     */
    @Query("SELECT bp FROM BillPayment bp WHERE bp.status = 'PROCESSING' " +
           "AND bp.processedAt < :cutoffTime")
    List<BillPayment> findStuckProcessingPayments(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Soft delete payment
     */
    @Query("UPDATE BillPayment bp SET bp.deletedAt = :now WHERE bp.id = :id")
    void softDelete(@Param("id") UUID id, @Param("now") LocalDateTime now);

    /**
     * Find payments by user ID and date range and status
     */
    @Query("SELECT bp FROM BillPayment bp WHERE bp.userId = :userId " +
           "AND bp.createdAt BETWEEN :startDate AND :endDate " +
           "AND bp.status = :status")
    Page<BillPayment> findByUserIdAndCreatedAtBetweenAndStatus(
            @Param("userId") String userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("status") String status,
            Pageable pageable);

    /**
     * Find payments by user ID and date range
     */
    @Query("SELECT bp FROM BillPayment bp WHERE bp.userId = :userId " +
           "AND bp.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY bp.createdAt DESC")
    Page<BillPayment> findByUserIdAndCreatedAtBetween(
            @Param("userId") String userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    /**
     * Find payments by user ID and date range (non-paginated)
     */
    @Query("SELECT bp FROM BillPayment bp WHERE bp.userId = :userId " +
           "AND bp.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY bp.createdAt DESC")
    List<BillPayment> findByUserIdAndCreatedAtBetween(
            @Param("userId") String userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Find payments by user ID and status
     */
    @Query("SELECT bp FROM BillPayment bp WHERE bp.userId = :userId AND bp.status = :status " +
           "ORDER BY bp.createdAt DESC")
    Page<BillPayment> findByUserIdAndStatus(
            @Param("userId") String userId,
            @Param("status") String status,
            Pageable pageable);

    /**
     * Find all payments by user ID
     */
    @Query("SELECT bp FROM BillPayment bp WHERE bp.userId = :userId ORDER BY bp.createdAt DESC")
    Page<BillPayment> findByUserId(@Param("userId") String userId, Pageable pageable);

    /**
     * Find completed payments by user and date range
     */
    @Query("SELECT bp FROM BillPayment bp WHERE bp.userId = :userId " +
           "AND bp.createdAt BETWEEN :startDate AND :endDate " +
           "AND bp.status = :status")
    List<BillPayment> findByUserIdAndCreatedAtBetweenAndStatus(
            @Param("userId") String userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("status") String status);
}
