package com.waqiti.billingorchestrator.repository;

import com.waqiti.billingorchestrator.entity.BillingCycle;
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
 * Repository for BillingCycle entities
 *
 * Production-ready with optimized queries and proper indexing
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
@Repository
public interface BillingCycleRepository extends JpaRepository<BillingCycle, UUID> {

    /**
     * Find billing cycles by customer ID
     */
    List<BillingCycle> findByCustomerId(UUID customerId);

    /**
     * Find billing cycles by customer ID with pagination
     */
    Page<BillingCycle> findByCustomerId(UUID customerId, Pageable pageable);

    /**
     * Find billing cycles by customer ID and status
     */
    List<BillingCycle> findByCustomerIdAndStatusIn(UUID customerId, List<BillingCycle.CycleStatus> statuses);

    /**
     * Find billing cycles by account ID
     */
    List<BillingCycle> findByAccountId(UUID accountId);

    /**
     * Find billing cycles by account ID with pagination
     */
    Page<BillingCycle> findByAccountId(UUID accountId, Pageable pageable);

    /**
     * Find active billing cycles
     */
    @Query("SELECT bc FROM BillingCycle bc WHERE bc.status IN ('OPEN', 'CLOSED', 'INVOICED', 'PARTIALLY_PAID')")
    List<BillingCycle> findActiveCycles();

    /**
     * Find overdue billing cycles
     */
    @Query("SELECT bc FROM BillingCycle bc WHERE bc.dueDate < :currentDate AND bc.balanceDue > 0 AND bc.status NOT IN ('PAID', 'WRITTEN_OFF')")
    List<BillingCycle> findOverdueCycles(@Param("currentDate") LocalDate currentDate);

    /**
     * Find billing cycles due for processing
     */
    @Query("SELECT bc FROM BillingCycle bc WHERE bc.cycleEndDate <= :currentDate AND bc.status = 'OPEN'")
    List<BillingCycle> findCyclesDueForProcessing(@Param("currentDate") LocalDate currentDate);

    /**
     * Find billing cycles by status
     */
    List<BillingCycle> findByStatus(BillingCycle.CycleStatus status);

    /**
     * Find billing cycles by status with pagination
     */
    Page<BillingCycle> findByStatus(BillingCycle.CycleStatus status, Pageable pageable);

    /**
     * Find billing cycles by invoice ID
     */
    Optional<BillingCycle> findByInvoiceId(UUID invoiceId);

    /**
     * Find billing cycle by invoice number
     */
    Optional<BillingCycle> findByInvoiceNumber(String invoiceNumber);

    /**
     * Find billing cycles by date range
     */
    @Query("SELECT bc FROM BillingCycle bc WHERE bc.cycleStartDate >= :startDate AND bc.cycleEndDate <= :endDate")
    List<BillingCycle> findByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Find billing cycles by date range with pagination
     */
    @Query("SELECT bc FROM BillingCycle bc WHERE bc.cycleStartDate >= :startDate AND bc.cycleEndDate <= :endDate")
    Page<BillingCycle> findByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, Pageable pageable);

    /**
     * Find billing cycles with auto pay enabled that are due
     */
    @Query("SELECT bc FROM BillingCycle bc WHERE bc.autoPayEnabled = true AND bc.dueDate <= :currentDate AND bc.balanceDue > 0 AND bc.status IN ('INVOICED', 'PARTIALLY_PAID')")
    List<BillingCycle> findAutoPayCyclesDue(@Param("currentDate") LocalDate currentDate);

    /**
     * Find billing cycles in dunning process
     */
    @Query("SELECT bc FROM BillingCycle bc WHERE bc.dunningLevel > 0 AND bc.status IN ('OVERDUE', 'DUNNING')")
    List<BillingCycle> findCyclesInDunning();

    /**
     * Count billing cycles by customer and status
     */
    long countByCustomerIdAndStatus(UUID customerId, BillingCycle.CycleStatus status);

    /**
     * Find billing cycles by customer type
     */
    List<BillingCycle> findByCustomerType(BillingCycle.CustomerType customerType);

    /**
     * Find billing cycles by frequency
     */
    List<BillingCycle> findByBillingFrequency(BillingCycle.BillingFrequency frequency);

    /**
     * Find the latest billing cycle for a customer
     */
    Optional<BillingCycle> findTopByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    /**
     * Find billing cycles that need invoice generation
     */
    @Query("SELECT bc FROM BillingCycle bc WHERE bc.status = 'CLOSED' AND bc.invoiceGenerated = false")
    List<BillingCycle> findCyclesNeedingInvoiceGeneration();

    /**
     * Find billing cycles with generated but unsent invoices
     */
    @Query("SELECT bc FROM BillingCycle bc WHERE bc.invoiceGenerated = true AND bc.invoiceSent = false")
    List<BillingCycle> findCyclesWithUnsentInvoices();

    /**
     * Find cycles by status and customer ID with pagination
     */
    Page<BillingCycle> findByCustomerIdAndStatus(UUID customerId, BillingCycle.CycleStatus status, Pageable pageable);

    /**
     * Find cycles in grace period
     */
    @Query("SELECT bc FROM BillingCycle bc WHERE bc.dueDate < :currentDate AND bc.gracePeriodEndDate >= :currentDate AND bc.balanceDue > 0")
    List<BillingCycle> findCyclesInGracePeriod(@Param("currentDate") LocalDate currentDate);

    /**
     * Calculate total revenue by date range
     */
    @Query("SELECT SUM(bc.totalAmount) FROM BillingCycle bc WHERE bc.cycleStartDate >= :startDate AND bc.cycleEndDate <= :endDate AND bc.status = 'PAID'")
    Optional<java.math.BigDecimal> calculateTotalRevenue(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Calculate outstanding balance
     */
    @Query("SELECT SUM(bc.balanceDue) FROM BillingCycle bc WHERE bc.balanceDue > 0 AND bc.status NOT IN ('PAID', 'WRITTEN_OFF')")
    Optional<java.math.BigDecimal> calculateOutstandingBalance();
}
