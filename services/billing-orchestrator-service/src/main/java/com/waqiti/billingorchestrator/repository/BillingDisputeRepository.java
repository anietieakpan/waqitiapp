package com.waqiti.billingorchestrator.repository;

import com.waqiti.billingorchestrator.entity.BillingDispute;
import com.waqiti.billingorchestrator.entity.BillingDispute.DisputeStatus;
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
 * Repository for BillingDispute entities
 *
 * @author Waqiti Billing Team
 * @since 1.0
 */
@Repository
public interface BillingDisputeRepository extends JpaRepository<BillingDispute, UUID> {

    /**
     * Find all disputes for a customer
     */
    Page<BillingDispute> findByCustomerId(UUID customerId, Pageable pageable);

    /**
     * Find disputes for a specific billing cycle
     */
    List<BillingDispute> findByBillingCycleId(UUID billingCycleId);

    /**
     * Find disputes by status
     */
    Page<BillingDispute> findByStatus(DisputeStatus status, Pageable pageable);

    /**
     * Find disputes assigned to a specific user
     */
    Page<BillingDispute> findByAssignedTo(UUID assignedTo, Pageable pageable);

    /**
     * Find escalated disputes
     */
    Page<BillingDispute> findByEscalatedTrue(Pageable pageable);

    /**
     * Find disputes with SLA breach
     */
    @Query("SELECT d FROM BillingDispute d WHERE d.slaDeadline < :now AND d.status IN :activeStatuses")
    List<BillingDispute> findSlaBreachedDisputes(
        @Param("now") LocalDateTime now,
        @Param("activeStatuses") List<DisputeStatus> activeStatuses
    );

    /**
     * Find disputes approaching SLA deadline (within 24 hours)
     */
    @Query("SELECT d FROM BillingDispute d WHERE d.slaDeadline BETWEEN :now AND :deadline " +
           "AND d.status IN :activeStatuses")
    List<BillingDispute> findDisputesApproachingSla(
        @Param("now") LocalDateTime now,
        @Param("deadline") LocalDateTime deadline,
        @Param("activeStatuses") List<DisputeStatus> activeStatuses
    );

    /**
     * Find unassigned disputes
     */
    @Query("SELECT d FROM BillingDispute d WHERE d.assignedTo IS NULL AND d.status = :status")
    List<BillingDispute> findUnassignedDisputes(@Param("status") DisputeStatus status);

    /**
     * Count active disputes by customer
     */
    @Query("SELECT COUNT(d) FROM BillingDispute d WHERE d.customerId = :customerId " +
           "AND d.status IN ('SUBMITTED', 'UNDER_REVIEW', 'PENDING_MERCHANT_RESPONSE', 'ESCALATED')")
    long countActiveDisputesByCustomer(@Param("customerId") UUID customerId);

    /**
     * Find disputes requiring merchant response
     */
    @Query("SELECT d FROM BillingDispute d WHERE d.status = 'PENDING_MERCHANT_RESPONSE' " +
           "AND d.merchantResponseDeadline < :deadline")
    List<BillingDispute> findOverdueMerchantResponses(@Param("deadline") LocalDateTime deadline);

    /**
     * Find disputes by customer and date range
     */
    @Query("SELECT d FROM BillingDispute d WHERE d.customerId = :customerId " +
           "AND d.submittedAt BETWEEN :startDate AND :endDate")
    List<BillingDispute> findByCustomerAndDateRange(
        @Param("customerId") UUID customerId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
}
