package com.waqiti.transaction.repository;

import com.waqiti.transaction.domain.Reconciliation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Reconciliation Management
 *
 * Manages reconciliation records for:
 * - Daily transaction reconciliation
 * - Payment provider reconciliation
 * - Settlement file reconciliation
 * - Batch reconciliation
 * - Ledger reconciliation
 *
 * CRITICAL: This repository was missing and causing runtime NullPointerException.
 *
 * Compliance:
 * - SOX: Daily reconciliation required
 * - PCI-DSS: Payment reconciliation audit trail
 * - Internal Controls: Independent verification
 *
 * @author Waqiti Platform Team
 * @since 2025-10-31 - CRITICAL FIX
 */
@Repository
public interface ReconciliationRepository extends JpaRepository<Reconciliation, UUID> {

    /**
     * Find reconciliation by date and type
     */
    @Query("SELECT r FROM Reconciliation r WHERE r.reconciliationDate = :date " +
           "AND r.reconciliationType = :type")
    Optional<Reconciliation> findByDateAndType(
        @Param("date") LocalDateTime date,
        @Param("type") String type
    );

    /**
     * Find all reconciliations for a date range
     */
    @Query("SELECT r FROM Reconciliation r WHERE r.reconciliationDate BETWEEN :startDate AND :endDate " +
           "ORDER BY r.reconciliationDate DESC")
    List<Reconciliation> findByDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find pending reconciliations
     */
    @Query("SELECT r FROM Reconciliation r WHERE r.status = 'PENDING' " +
           "ORDER BY r.reconciliationDate ASC")
    List<Reconciliation> findPendingReconciliations();

    /**
     * Find failed reconciliations requiring attention
     */
    @Query("SELECT r FROM Reconciliation r WHERE r.status = 'FAILED' " +
           "AND r.resolvedAt IS NULL " +
           "ORDER BY r.reconciliationDate ASC")
    List<Reconciliation> findFailedReconciliations();

    /**
     * Find reconciliations with discrepancies
     */
    @Query("SELECT r FROM Reconciliation r WHERE r.discrepancyCount > 0 " +
           "AND r.resolvedAt IS NULL")
    List<Reconciliation> findReconciliationsWithDiscrepancies();

    /**
     * Find reconciliations by provider
     */
    @Query("SELECT r FROM Reconciliation r WHERE r.providerId = :providerId " +
           "AND r.reconciliationDate BETWEEN :startDate AND :endDate")
    List<Reconciliation> findByProvider(
        @Param("providerId") String providerId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find reconciliations by batch ID
     */
    @Query("SELECT r FROM Reconciliation r WHERE r.batchId = :batchId")
    Optional<Reconciliation> findByBatchId(@Param("batchId") String batchId);

    /**
     * Find reconciliations requiring manual review
     */
    @Query("SELECT r FROM Reconciliation r WHERE r.requiresManualReview = true " +
           "AND r.reviewedAt IS NULL")
    List<Reconciliation> findRequiringManualReview();

    /**
     * Get reconciliation statistics for a period
     */
    @Query("SELECT " +
           "COUNT(r) as total, " +
           "SUM(CASE WHEN r.status = 'COMPLETED' THEN 1 ELSE 0 END) as completed, " +
           "SUM(CASE WHEN r.status = 'FAILED' THEN 1 ELSE 0 END) as failed, " +
           "SUM(r.matchedCount) as totalMatched, " +
           "SUM(r.unmatchedCount) as totalUnmatched, " +
           "SUM(r.discrepancyCount) as totalDiscrepancies " +
           "FROM Reconciliation r " +
           "WHERE r.reconciliationDate BETWEEN :startDate AND :endDate")
    Object[] getStatistics(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find overdue reconciliations (not completed within SLA)
     */
    @Query("SELECT r FROM Reconciliation r WHERE r.status = 'PENDING' " +
           "AND r.dueDatetime < :currentTime")
    List<Reconciliation> findOverdueReconciliations(@Param("currentTime") LocalDateTime currentTime);

    /**
     * Find reconciliations by settlement file
     */
    @Query("SELECT r FROM Reconciliation r WHERE r.settlementFileId = :fileId")
    Optional<Reconciliation> findBySettlementFileId(@Param("fileId") String fileId);

    /**
     * Check if reconciliation exists for date and type
     */
    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END " +
           "FROM Reconciliation r " +
           "WHERE r.reconciliationDate = :date " +
           "AND r.reconciliationType = :type")
    boolean existsByDateAndType(
        @Param("date") LocalDateTime date,
        @Param("type") String type
    );

    /**
     * Find latest reconciliation by type
     */
    @Query("SELECT r FROM Reconciliation r WHERE r.reconciliationType = :type " +
           "ORDER BY r.reconciliationDate DESC LIMIT 1")
    Optional<Reconciliation> findLatestByType(@Param("type") String type);

    /**
     * Find reconciliations with high discrepancy amounts
     */
    @Query("SELECT r FROM Reconciliation r WHERE r.totalDiscrepancyAmount > :threshold " +
           "AND r.resolvedAt IS NULL")
    List<Reconciliation> findHighValueDiscrepancies(@Param("threshold") java.math.BigDecimal threshold);

    /**
     * Find reconciliations completed by user
     */
    @Query("SELECT r FROM Reconciliation r WHERE r.completedBy = :userId " +
           "ORDER BY r.completedAt DESC")
    List<Reconciliation> findCompletedByUser(@Param("userId") String userId);
}
