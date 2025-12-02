package com.waqiti.ledger.repository;

import com.waqiti.ledger.domain.ReconciliationDiscrepancy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ReconciliationDiscrepancy entities
 */
@Repository
public interface ReconciliationDiscrepancyRepository extends JpaRepository<ReconciliationDiscrepancy, UUID> {

    /**
     * Find discrepancy by number
     */
    Optional<ReconciliationDiscrepancy> findByDiscrepancyNumber(String discrepancyNumber);

    /**
     * Check if discrepancy number exists
     */
    boolean existsByDiscrepancyNumber(String discrepancyNumber);

    /**
     * Find discrepancies by status
     */
    Page<ReconciliationDiscrepancy> findByStatusOrderByCreatedAtDesc(
            ReconciliationDiscrepancy.DiscrepancyStatus status, Pageable pageable);

    /**
     * Find discrepancies by account
     */
    Page<ReconciliationDiscrepancy> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

    /**
     * Find discrepancies by account and status
     */
    Page<ReconciliationDiscrepancy> findByStatusAndAccountIdOrderByCreatedAtDesc(
            ReconciliationDiscrepancy.DiscrepancyStatus status, UUID accountId, Pageable pageable);

    /**
     * Find discrepancies by type
     */
    Page<ReconciliationDiscrepancy> findByDiscrepancyTypeOrderByCreatedAtDesc(
            ReconciliationDiscrepancy.DiscrepancyType discrepancyType, Pageable pageable);

    /**
     * Find discrepancies by priority
     */
    Page<ReconciliationDiscrepancy> findByPriorityOrderByCreatedAtDesc(
            ReconciliationDiscrepancy.Priority priority, Pageable pageable);

    /**
     * Find all discrepancies ordered by creation date
     */
    Page<ReconciliationDiscrepancy> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Find open discrepancies
     */
    @Query("SELECT rd FROM ReconciliationDiscrepancy rd WHERE rd.status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS', 'PENDING_APPROVAL') " +
           "ORDER BY rd.priority DESC, rd.createdAt ASC")
    Page<ReconciliationDiscrepancy> findOpenDiscrepancies(Pageable pageable);

    /**
     * Find discrepancies assigned to user
     */
    Page<ReconciliationDiscrepancy> findByAssignedToOrderByDueDateAsc(String assignedTo, Pageable pageable);

    /**
     * Find overdue discrepancies
     */
    @Query("SELECT rd FROM ReconciliationDiscrepancy rd WHERE rd.dueDate < :currentDate " +
           "AND rd.status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS') " +
           "ORDER BY rd.dueDate ASC")
    Page<ReconciliationDiscrepancy> findOverdueDiscrepancies(@Param("currentDate") LocalDate currentDate,
                                                           Pageable pageable);

    /**
     * Find discrepancies requiring escalation
     */
    @Query("SELECT rd FROM ReconciliationDiscrepancy rd WHERE rd.dueDate < :cutoffDate " +
           "AND rd.status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS') " +
           "AND rd.escalatedAt IS NULL " +
           "ORDER BY rd.dueDate ASC")
    List<ReconciliationDiscrepancy> findDiscrepanciesForEscalation(@Param("cutoffDate") LocalDate cutoffDate);

    /**
     * Find discrepancies by discovered date range
     */
    Page<ReconciliationDiscrepancy> findByDiscoveredDateBetweenOrderByDiscoveredDateDesc(
            LocalDate startDate, LocalDate endDate, Pageable pageable);

    /**
     * Find discrepancies by amount range
     */
    @Query("SELECT rd FROM ReconciliationDiscrepancy rd WHERE rd.amount BETWEEN :minAmount AND :maxAmount " +
           "ORDER BY rd.amount DESC")
    Page<ReconciliationDiscrepancy> findByAmountRange(@Param("minAmount") java.math.BigDecimal minAmount,
                                                    @Param("maxAmount") java.math.BigDecimal maxAmount,
                                                    Pageable pageable);

    /**
     * Find high-value discrepancies
     */
    @Query("SELECT rd FROM ReconciliationDiscrepancy rd WHERE rd.amount > :threshold " +
           "ORDER BY rd.amount DESC")
    Page<ReconciliationDiscrepancy> findHighValueDiscrepancies(@Param("threshold") java.math.BigDecimal threshold,
                                                             Pageable pageable);

    /**
     * Find discrepancies by source system
     */
    Page<ReconciliationDiscrepancy> findBySourceSystemOrderByCreatedAtDesc(String sourceSystem, Pageable pageable);

    /**
     * Find discrepancies by reconciliation
     */
    Page<ReconciliationDiscrepancy> findByReconciliationIdOrderByCreatedAtDesc(UUID reconciliationId,
                                                                             Pageable pageable);

    /**
     * Find discrepancies resolved by user
     */
    Page<ReconciliationDiscrepancy> findByResolvedByOrderByResolvedAtDesc(String resolvedBy, Pageable pageable);

    /**
     * Find discrepancies resolved in date range
     */
    @Query("SELECT rd FROM ReconciliationDiscrepancy rd WHERE rd.resolvedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY rd.resolvedAt DESC")
    Page<ReconciliationDiscrepancy> findDiscrepanciesResolvedBetween(@Param("startDate") LocalDateTime startDate,
                                                                   @Param("endDate") LocalDateTime endDate,
                                                                   Pageable pageable);

    /**
     * Find aged discrepancies
     */
    @Query("SELECT rd FROM ReconciliationDiscrepancy rd WHERE rd.discoveredDate <= :cutoffDate " +
           "AND rd.status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS') " +
           "ORDER BY rd.discoveredDate ASC")
    Page<ReconciliationDiscrepancy> findAgedDiscrepancies(@Param("cutoffDate") LocalDate cutoffDate,
                                                        Pageable pageable);

    /**
     * Complex search with multiple criteria
     */
    @Query("SELECT rd FROM ReconciliationDiscrepancy rd WHERE " +
           "(:accountId IS NULL OR rd.accountId = :accountId) AND " +
           "(:status IS NULL OR rd.status = :status) AND " +
           "(:discrepancyType IS NULL OR rd.discrepancyType = :discrepancyType) AND " +
           "(:priority IS NULL OR rd.priority = :priority) AND " +
           "(:assignedTo IS NULL OR rd.assignedTo = :assignedTo) AND " +
           "(:startDate IS NULL OR rd.discoveredDate >= :startDate) AND " +
           "(:endDate IS NULL OR rd.discoveredDate <= :endDate) AND " +
           "(:minAmount IS NULL OR rd.amount >= :minAmount) AND " +
           "(:maxAmount IS NULL OR rd.amount <= :maxAmount) " +
           "ORDER BY rd.createdAt DESC")
    Page<ReconciliationDiscrepancy> searchDiscrepancies(
            @Param("accountId") UUID accountId,
            @Param("status") ReconciliationDiscrepancy.DiscrepancyStatus status,
            @Param("discrepancyType") ReconciliationDiscrepancy.DiscrepancyType discrepancyType,
            @Param("priority") ReconciliationDiscrepancy.Priority priority,
            @Param("assignedTo") String assignedTo,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("minAmount") java.math.BigDecimal minAmount,
            @Param("maxAmount") java.math.BigDecimal maxAmount,
            Pageable pageable);

    /**
     * Get discrepancy statistics by status
     */
    @Query("SELECT rd.status, COUNT(rd), SUM(rd.amount) FROM ReconciliationDiscrepancy rd " +
           "GROUP BY rd.status")
    List<Object[]> getDiscrepancyStatisticsByStatus();

    /**
     * Get discrepancy statistics by type
     */
    @Query("SELECT rd.discrepancyType, COUNT(rd), SUM(rd.amount) FROM ReconciliationDiscrepancy rd " +
           "GROUP BY rd.discrepancyType")
    List<Object[]> getDiscrepancyStatisticsByType();

    /**
     * Get discrepancy statistics by priority
     */
    @Query("SELECT rd.priority, COUNT(rd), SUM(rd.amount) FROM ReconciliationDiscrepancy rd " +
           "GROUP BY rd.priority")
    List<Object[]> getDiscrepancyStatisticsByPriority();

    /**
     * Get discrepancy aging analysis
     */
    @Query("SELECT " +
           "CASE " +
           "  WHEN rd.agingDays <= 7 THEN '0-7 days' " +
           "  WHEN rd.agingDays <= 30 THEN '8-30 days' " +
           "  WHEN rd.agingDays <= 90 THEN '31-90 days' " +
           "  ELSE '90+ days' " +
           "END as agingBucket, " +
           "COUNT(rd), SUM(rd.amount) " +
           "FROM ReconciliationDiscrepancy rd " +
           "WHERE rd.status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS') " +
           "GROUP BY " +
           "CASE " +
           "  WHEN rd.agingDays <= 7 THEN '0-7 days' " +
           "  WHEN rd.agingDays <= 30 THEN '8-30 days' " +
           "  WHEN rd.agingDays <= 90 THEN '31-90 days' " +
           "  ELSE '90+ days' " +
           "END")
    List<Object[]> getDiscrepancyAgingAnalysis();

    /**
     * Get resolution statistics
     */
    @Query("SELECT rd.resolutionType, COUNT(rd), AVG(rd.amount) " +
           "FROM ReconciliationDiscrepancy rd " +
           "WHERE rd.status = 'RESOLVED' AND rd.resolutionType IS NOT NULL " +
           "GROUP BY rd.resolutionType")
    List<Object[]> getResolutionStatistics();

    /**
     * Find discrepancies by currency
     */
    Page<ReconciliationDiscrepancy> findByCurrencyOrderByCreatedAtDesc(String currency, Pageable pageable);

    /**
     * Find recurring discrepancies
     */
    @Query("SELECT rd.discrepancyType, rd.accountId, COUNT(rd) " +
           "FROM ReconciliationDiscrepancy rd " +
           "WHERE rd.discoveredDate >= :startDate " +
           "GROUP BY rd.discrepancyType, rd.accountId " +
           "HAVING COUNT(rd) > :threshold " +
           "ORDER BY COUNT(rd) DESC")
    List<Object[]> findRecurringDiscrepancies(@Param("startDate") LocalDate startDate,
                                            @Param("threshold") Long threshold);

    /**
     * Get total outstanding amount by account
     */
    @Query("SELECT rd.accountId, SUM(rd.amount) " +
           "FROM ReconciliationDiscrepancy rd " +
           "WHERE rd.status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS') " +
           "GROUP BY rd.accountId")
    List<Object[]> getTotalOutstandingByAccount();

    /**
     * Find discrepancies requiring approval
     */
    @Query("SELECT rd FROM ReconciliationDiscrepancy rd WHERE rd.status = 'PENDING_APPROVAL' " +
           "ORDER BY rd.createdAt ASC")
    Page<ReconciliationDiscrepancy> findDiscrepanciesRequiringApproval(Pageable pageable);

    /**
     * Get next discrepancy number sequence
     */
    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(rd.discrepancyNumber, 4) AS integer)), 0) + 1 " +
           "FROM ReconciliationDiscrepancy rd WHERE rd.discrepancyNumber LIKE 'RD-%'")
    Integer getNextDiscrepancyNumber();

    /**
     * Find discrepancies by account and status, ordered by creation date ascending
     */
    List<ReconciliationDiscrepancy> findByAccountIdAndStatusOrderByCreatedAtAsc(
            UUID accountId, ReconciliationDiscrepancy.DiscrepancyStatus status);

    /**
     * Update aging days for all open discrepancies
     */
    @Modifying
    @Query("UPDATE ReconciliationDiscrepancy rd SET rd.agingDays = " +
           "CAST(FUNCTION('DATEDIFF', CURRENT_DATE, rd.discoveredDate) AS integer) " +
           "WHERE rd.status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS')")
    int updateAgingDaysForOpenDiscrepancies();
}