package com.waqiti.reconciliation.repository;

import com.waqiti.reconciliation.domain.Discrepancy;
import com.waqiti.reconciliation.domain.Discrepancy.DiscrepancyStatus;
import com.waqiti.reconciliation.domain.Discrepancy.DiscrepancyType;
import com.waqiti.reconciliation.domain.Discrepancy.Severity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * DiscrepancyJpaRepository - JPA repository for Discrepancy entities
 *
 * Manages discrepancies found during reconciliation with SLA tracking and workflow support.
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 */
@Repository
public interface DiscrepancyJpaRepository extends JpaRepository<Discrepancy, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Discrepancy d WHERE d.id = :id AND d.deleted = false")
    Optional<Discrepancy> findByIdForUpdate(@Param("id") String id);

    @Query("SELECT d FROM Discrepancy d WHERE d.reconciliationBatchId = :batchId AND d.deleted = false")
    List<Discrepancy> findByReconciliationBatchId(@Param("batchId") String batchId);

    @Query("SELECT d FROM Discrepancy d WHERE d.status = :status AND d.deleted = false")
    Page<Discrepancy> findByStatus(@Param("status") DiscrepancyStatus status, Pageable pageable);

    @Query("SELECT d FROM Discrepancy d WHERE d.severity = :severity AND d.deleted = false")
    Page<Discrepancy> findBySeverity(@Param("severity") Severity severity, Pageable pageable);

    @Query("SELECT d FROM Discrepancy d WHERE d.discrepancyType = :type AND d.deleted = false")
    List<Discrepancy> findByDiscrepancyType(@Param("type") DiscrepancyType type);

    @Query("SELECT d FROM Discrepancy d WHERE " +
           "d.status = :status AND d.severity = :severity AND d.deleted = false")
    List<Discrepancy> findByStatusAndSeverity(
        @Param("status") DiscrepancyStatus status,
        @Param("severity") Severity severity
    );

    @Query("SELECT d FROM Discrepancy d WHERE d.assignedTo = :userId " +
           "AND d.status IN ('ASSIGNED', 'IN_PROGRESS') AND d.deleted = false")
    List<Discrepancy> findActiveDiscrepanciesForUser(@Param("userId") String userId);

    @Query("SELECT d FROM Discrepancy d WHERE " +
           "d.status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS') " +
           "AND d.slaDueDate < :now " +
           "AND d.slaBreach = false " +
           "AND d.deleted = false")
    List<Discrepancy> findDiscrepanciesBreachingSla(@Param("now") LocalDateTime now);

    @Query("SELECT d FROM Discrepancy d WHERE " +
           "d.status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS') " +
           "AND d.slaDueDate BETWEEN :now AND :warningThreshold " +
           "AND d.deleted = false")
    List<Discrepancy> findDiscrepanciesNearingSla(
        @Param("now") LocalDateTime now,
        @Param("warningThreshold") LocalDateTime warningThreshold
    );

    @Modifying
    @Query("UPDATE Discrepancy d SET d.slaBreach = true WHERE " +
           "d.id IN :ids AND d.deleted = false")
    void markDiscrepanciesAsSlaBreach(@Param("ids") List<String> ids);

    @Query("SELECT COUNT(d) FROM Discrepancy d WHERE " +
           "d.reconciliationBatchId = :batchId AND d.severity = :severity AND d.deleted = false")
    Long countBySeverityInBatch(
        @Param("batchId") String batchId,
        @Param("severity") Severity severity
    );

    @Query("SELECT COUNT(d) FROM Discrepancy d WHERE " +
           "d.status = :status AND d.deleted = false")
    Long countByStatus(@Param("status") DiscrepancyStatus status);

    @Query("SELECT d FROM Discrepancy d WHERE " +
           "(d.sourceItemId = :itemId OR d.targetItemId = :itemId) " +
           "AND d.deleted = false")
    List<Discrepancy> findByRelatedItemId(@Param("itemId") String itemId);

    @Query("SELECT d FROM Discrepancy d WHERE " +
           "d.createdAt BETWEEN :startDate AND :endDate " +
           "AND d.deleted = false " +
           "ORDER BY d.severity DESC, d.createdAt ASC")
    List<Discrepancy> findDiscrepanciesInDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT d.discrepancyType as type, COUNT(d) as count " +
           "FROM Discrepancy d WHERE " +
           "d.createdAt BETWEEN :startDate AND :endDate " +
           "AND d.deleted = false " +
           "GROUP BY d.discrepancyType")
    List<Object[]> getDiscrepancyTypeStatistics(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT d FROM Discrepancy d WHERE " +
           "d.status = 'RESOLVED' " +
           "AND d.resolvedAt < :cutoffDate " +
           "AND d.deleted = false")
    List<Discrepancy> findResolvedDiscrepanciesEligibleForArchiving(
        @Param("cutoffDate") LocalDateTime cutoffDate
    );

    @Query("SELECT d FROM Discrepancy d WHERE " +
           "d.status IN ('OPEN') " +
           "AND d.severity IN ('CRITICAL', 'HIGH') " +
           "AND d.deleted = false " +
           "ORDER BY d.severity DESC, d.createdAt ASC")
    List<Discrepancy> findUnassignedHighPriorityDiscrepancies();
}
