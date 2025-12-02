package com.waqiti.billingorchestrator.repository;

import com.waqiti.billingorchestrator.entity.Subscription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Subscription entities
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    /**
     * Find subscriptions by customer ID
     */
    List<Subscription> findByCustomerId(UUID customerId);

    /**
     * Find subscriptions by customer ID with pagination
     */
    Page<Subscription> findByCustomerId(UUID customerId, Pageable pageable);

    /**
     * Find subscriptions by account ID
     */
    List<Subscription> findByAccountId(UUID accountId);

    /**
     * Find subscriptions by status
     */
    List<Subscription> findByStatus(Subscription.SubscriptionStatus status);

    /**
     * Find active subscriptions for customer
     */
    @Query("SELECT s FROM Subscription s WHERE s.customerId = :customerId AND s.status IN ('ACTIVE', 'TRIAL')")
    List<Subscription> findActiveSubscriptionsByCustomer(@Param("customerId") UUID customerId);

    /**
     * Find subscriptions due for billing
     */
    @Query("SELECT s FROM Subscription s WHERE s.nextBillingDate <= :currentDate AND s.status = 'ACTIVE'")
    List<Subscription> findSubscriptionsDueForBilling(@Param("currentDate") LocalDate currentDate);

    /**
     * Find subscriptions in trial period
     */
    @Query("SELECT s FROM Subscription s WHERE s.status = 'TRIAL' AND s.trialEnd >= :currentDate")
    List<Subscription> findSubscriptionsInTrial(@Param("currentDate") LocalDate currentDate);

    /**
     * Find subscriptions with trial ending soon
     */
    @Query("SELECT s FROM Subscription s WHERE s.status = 'TRIAL' AND s.trialEnd BETWEEN :startDate AND :endDate")
    List<Subscription> findSubscriptionsWithTrialEndingSoon(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Find subscriptions by plan
     */
    List<Subscription> findByPlanId(UUID planId);

    /**
     * Count active subscriptions by plan
     */
    @Query("SELECT COUNT(s) FROM Subscription s WHERE s.planId = :planId AND s.status = 'ACTIVE'")
    long countActiveSubscriptionsByPlan(@Param("planId") UUID planId);

    /**
     * Find subscription by customer and plan
     */
    Optional<Subscription> findByCustomerIdAndPlanId(UUID customerId, UUID planId);

    /**
     * Find paused subscriptions
     */
    List<Subscription> findByStatus(Subscription.SubscriptionStatus status, Pageable pageable);

    /**
     * Calculate monthly recurring revenue (MRR)
     */
    @Query("SELECT SUM(s.price) FROM Subscription s WHERE s.status = 'ACTIVE' AND s.billingInterval = 'MONTHLY'")
    Optional<java.math.BigDecimal> calculateMonthlyRecurringRevenue();

    /**
     * Find subscriptions expiring soon
     */
    @Query("SELECT s FROM Subscription s WHERE s.endDate IS NOT NULL AND s.endDate BETWEEN :startDate AND :endDate AND s.status = 'ACTIVE'")
    List<Subscription> findSubscriptionsExpiringSoon(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
