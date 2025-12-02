package com.waqiti.payment.repository;

import com.waqiti.payment.entity.Payment;
import com.waqiti.payment.entity.PaymentStatus;
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
 * Payment Repository - PRODUCTION READY
 * Enhanced with merchant analytics queries for fee calculations
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentId(UUID paymentId);

    List<Payment> findByUserId(UUID userId);

    List<Payment> findByStatus(PaymentStatus status);

    List<Payment> findByUserIdAndStatus(UUID userId, PaymentStatus status);

    /**
     * PRODUCTION: Find payments by merchant ID created after a specific date
     * Used for merchant volume calculations and risk assessment
     */
    List<Payment> findByMerchantIdAndCreatedAtAfter(UUID merchantId, LocalDateTime createdAt);

    /**
     * PRODUCTION: Calculate total volume for merchant in date range
     * Optimized query for performance (avoids loading full entities)
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
           "WHERE p.merchantId = :merchantId " +
           "AND p.createdAt BETWEEN :startDate AND :endDate " +
           "AND (p.status = 'COMPLETED' OR p.status = 'SETTLED')")
    BigDecimal calculateMerchantVolumeInRange(
            @Param("merchantId") UUID merchantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * PRODUCTION: Count transactions by merchant in date range
     */
    @Query("SELECT COUNT(p) FROM Payment p " +
           "WHERE p.merchantId = :merchantId " +
           "AND p.createdAt BETWEEN :startDate AND :endDate")
    long countMerchantTransactionsInRange(
            @Param("merchantId") UUID merchantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * PRODUCTION: Get merchant failure rate (for risk assessment)
     */
    @Query("SELECT COUNT(p) FROM Payment p " +
           "WHERE p.merchantId = :merchantId " +
           "AND p.createdAt >= :since " +
           "AND (p.status = 'FAILED' OR p.status = 'DECLINED')")
    long countMerchantFailedTransactionsSince(
            @Param("merchantId") UUID merchantId,
            @Param("since") LocalDateTime since
    );
}
