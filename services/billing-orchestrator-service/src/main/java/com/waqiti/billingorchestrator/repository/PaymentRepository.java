package com.waqiti.billingorchestrator.repository;

import com.waqiti.billingorchestrator.entity.Payment;
import com.waqiti.billingorchestrator.entity.BillingCycle;
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
 * Repository for Payment entities
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Find payments by billing cycle
     */
    List<Payment> findByBillingCycle(BillingCycle billingCycle);

    /**
     * Find payments by billing cycle ID
     */
    List<Payment> findByBillingCycleId(UUID billingCycleId);

    /**
     * Find payment by transaction ID
     */
    Optional<Payment> findByTransactionId(String transactionId);

    /**
     * Find payments by customer ID
     */
    @Query("SELECT p FROM Payment p WHERE p.billingCycle.customerId = :customerId")
    List<Payment> findByCustomerId(@Param("customerId") UUID customerId);

    /**
     * Find payments by customer ID with pagination
     */
    @Query("SELECT p FROM Payment p WHERE p.billingCycle.customerId = :customerId ORDER BY p.paymentDate DESC")
    Page<Payment> findByCustomerId(@Param("customerId") UUID customerId, Pageable pageable);

    /**
     * Find payments by status
     */
    List<Payment> findByStatus(Payment.PaymentStatus status);

    /**
     * Find payments by payment method
     */
    List<Payment> findByPaymentMethod(Payment.PaymentMethod paymentMethod);

    /**
     * Find failed payments requiring retry
     */
    @Query("SELECT p FROM Payment p WHERE p.status = 'FAILED' AND p.retryCount < p.maxRetries")
    List<Payment> findFailedPaymentsForRetry();

    /**
     * Find pending payments
     */
    List<Payment> findByStatusOrderByPaymentDateAsc(Payment.PaymentStatus status);

    /**
     * Calculate total payments by date range
     */
    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.paymentDate BETWEEN :startDate AND :endDate AND p.status = 'COMPLETED'")
    Optional<java.math.BigDecimal> calculateTotalPayments(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Find recent payments
     */
    @Query("SELECT p FROM Payment p WHERE p.paymentDate >= :since ORDER BY p.paymentDate DESC")
    List<Payment> findRecentPayments(@Param("since") LocalDateTime since);
}
