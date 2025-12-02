package com.waqiti.billingorchestrator.repository;

import com.waqiti.billingorchestrator.entity.BillingEvent;
import com.waqiti.billingorchestrator.entity.BillingCycle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for BillingEvent entities
 *
 * Comprehensive event audit trail repository
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
@Repository
public interface BillingEventRepository extends JpaRepository<BillingEvent, UUID> {

    /**
     * Find events by billing cycle
     */
    List<BillingEvent> findByBillingCycle(BillingCycle billingCycle);

    /**
     * Find events by billing cycle with pagination
     */
    Page<BillingEvent> findByBillingCycle(BillingCycle billingCycle, Pageable pageable);

    /**
     * Find events by billing cycle ID
     */
    List<BillingEvent> findByBillingCycleId(UUID billingCycleId);

    /**
     * Find events by billing cycle ID ordered by timestamp descending
     */
    List<BillingEvent> findByBillingCycleIdOrderByEventTimestampDesc(UUID billingCycleId);

    /**
     * Find events by event type
     */
    List<BillingEvent> findByEventType(BillingEvent.EventType eventType);

    /**
     * Find events by event type and date range
     */
    @Query("SELECT be FROM BillingEvent be WHERE be.eventType = :eventType AND be.eventTimestamp BETWEEN :startDate AND :endDate")
    List<BillingEvent> findByEventTypeAndDateRange(
            @Param("eventType") BillingEvent.EventType eventType,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Find failed events requiring retry
     */
    @Query("SELECT be FROM BillingEvent be WHERE be.nextRetryAt <= :currentTime AND be.retryCount < be.maxRetries")
    List<BillingEvent> findEventsRequiringRetry(@Param("currentTime") LocalDateTime currentTime);

    /**
     * Find events requiring manual intervention
     */
    @Query("SELECT be FROM BillingEvent be WHERE (be.eventType IN ('ERROR_OCCURRED', 'DISPUTE_OPENED', 'COMPLIANCE_CHECK_FAILED') OR be.retryCount >= be.maxRetries) AND be.eventTimestamp >= :since")
    List<BillingEvent> findEventsRequiringManualIntervention(@Param("since") LocalDateTime since);

    /**
     * Find payment events for a billing cycle
     */
    @Query("SELECT be FROM BillingEvent be WHERE be.billingCycle.id = :cycleId AND be.eventType IN ('PAYMENT_INITIATED', 'PAYMENT_SUCCEEDED', 'PAYMENT_FAILED', 'PARTIAL_PAYMENT_RECEIVED')")
    List<BillingEvent> findPaymentEvents(@Param("cycleId") UUID cycleId);

    /**
     * Find notification events
     */
    @Query("SELECT be FROM BillingEvent be WHERE be.eventType IN ('NOTIFICATION_SENT', 'NOTIFICATION_FAILED', 'NOTIFICATION_BOUNCED')")
    List<BillingEvent> findNotificationEvents();

    /**
     * Find unsent notifications
     */
    @Query("SELECT be FROM BillingEvent be WHERE be.notificationSent = false AND be.eventType = 'NOTIFICATION_FAILED' AND be.retryCount < 3")
    List<BillingEvent> findUnsentNotifications();

    /**
     * Count events by type for a billing cycle
     */
    long countByBillingCycleAndEventType(BillingCycle billingCycle, BillingEvent.EventType eventType);

    /**
     * Find recent events for a customer (via billing cycle)
     */
    @Query("SELECT be FROM BillingEvent be WHERE be.billingCycle.customerId = :customerId AND be.eventTimestamp >= :since ORDER BY be.eventTimestamp DESC")
    List<BillingEvent> findRecentEventsByCustomer(@Param("customerId") UUID customerId, @Param("since") LocalDateTime since);

    /**
     * Find error events
     */
    @Query("SELECT be FROM BillingEvent be WHERE be.errorCode IS NOT NULL ORDER BY be.eventTimestamp DESC")
    List<BillingEvent> findErrorEvents();

    /**
     * Find error events with pagination
     */
    @Query("SELECT be FROM BillingEvent be WHERE be.errorCode IS NOT NULL ORDER BY be.eventTimestamp DESC")
    Page<BillingEvent> findErrorEvents(Pageable pageable);

    /**
     * Find events by reference
     */
    List<BillingEvent> findByReferenceTypeAndReferenceId(String referenceType, String referenceId);

    /**
     * Find system generated events
     */
    List<BillingEvent> findBySystemGeneratedTrue();

    /**
     * Find manual intervention events
     */
    List<BillingEvent> findBySystemGeneratedFalse();

    /**
     * Count events by type in date range
     */
    @Query("SELECT COUNT(be) FROM BillingEvent be WHERE be.eventType = :eventType AND be.eventTimestamp BETWEEN :startDate AND :endDate")
    long countByEventTypeInDateRange(
            @Param("eventType") BillingEvent.EventType eventType,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}
