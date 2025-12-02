package com.waqiti.reconciliation.repository;

import com.waqiti.reconciliation.domain.ReconciliationBreak;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReconciliationBreakRepository extends JpaRepository<ReconciliationBreak, UUID> {

    /**
     * Find all unresolved breaks
     */
    @Query("SELECT rb FROM ReconciliationBreak rb WHERE rb.status NOT IN ('RESOLVED', 'CLOSED') ORDER BY rb.detectedAt DESC")
    List<ReconciliationBreak> findUnresolvedBreaks();

    /**
     * Find unresolved breaks with pagination
     */
    @Query("SELECT rb FROM ReconciliationBreak rb WHERE rb.status NOT IN ('RESOLVED', 'CLOSED') ORDER BY rb.detectedAt DESC")
    Page<ReconciliationBreak> findUnresolvedBreaks(Pageable pageable);

    /**
     * Find breaks by status
     */
    List<ReconciliationBreak> findByStatusOrderByDetectedAtDesc(ReconciliationBreak.BreakStatus status);

    /**
     * Find breaks by break type
     */
    List<ReconciliationBreak> findByBreakTypeOrderByDetectedAtDesc(ReconciliationBreak.BreakType breakType);

    /**
     * Find breaks by entity ID
     */
    List<ReconciliationBreak> findByEntityIdOrderByDetectedAtDesc(String entityId);

    /**
     * Find breaks detected within time range
     */
    List<ReconciliationBreak> findByDetectedAtBetweenOrderByDetectedAtDesc(
        LocalDateTime startTime, 
        LocalDateTime endTime
    );

    /**
     * Find breaks detected within time range with pagination
     */
    Page<ReconciliationBreak> findByDetectedAtBetween(
        LocalDateTime startTime, 
        LocalDateTime endTime, 
        Pageable pageable
    );

    /**
     * Find breaks by status and break type
     */
    List<ReconciliationBreak> findByStatusAndBreakTypeOrderByDetectedAtDesc(
        ReconciliationBreak.BreakStatus status,
        ReconciliationBreak.BreakType breakType
    );

    /**
     * Find high priority breaks (critical and high severity)
     */
    @Query("SELECT rb FROM ReconciliationBreak rb WHERE rb.severity IN ('CRITICAL', 'HIGH') AND rb.status NOT IN ('RESOLVED', 'CLOSED') ORDER BY rb.detectedAt DESC")
    List<ReconciliationBreak> findHighPriorityUnresolvedBreaks();

    /**
     * Find breaks that have been investigating for too long
     */
    @Query("SELECT rb FROM ReconciliationBreak rb WHERE rb.status = 'INVESTIGATING' AND rb.investigationStartedAt < :cutoffTime")
    List<ReconciliationBreak> findLongRunningInvestigations(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Find breaks requiring manual review
     */
    List<ReconciliationBreak> findByStatusOrderByDetectedAtDesc(ReconciliationBreak.BreakStatus status);

    /**
     * Count breaks by status
     */
    @Query("SELECT rb.status, COUNT(rb) FROM ReconciliationBreak rb GROUP BY rb.status")
    List<Object[]> countBreaksByStatus();

    /**
     * Count breaks by break type
     */
    @Query("SELECT rb.breakType, COUNT(rb) FROM ReconciliationBreak rb GROUP BY rb.breakType")
    List<Object[]> countBreaksByType();

    /**
     * Count breaks by severity
     */
    @Query("SELECT rb.severity, COUNT(rb) FROM ReconciliationBreak rb GROUP BY rb.severity")
    List<Object[]> countBreaksBySeverity();

    /**
     * Find breaks detected in the last N hours
     */
    @Query("SELECT rb FROM ReconciliationBreak rb WHERE rb.detectedAt >= :since ORDER BY rb.detectedAt DESC")
    List<ReconciliationBreak> findRecentBreaks(@Param("since") LocalDateTime since);

    /**
     * Find resolved breaks within date range
     */
    @Query("SELECT rb FROM ReconciliationBreak rb WHERE rb.status = 'RESOLVED' AND rb.resolvedAt BETWEEN :startTime AND :endTime ORDER BY rb.resolvedAt DESC")
    List<ReconciliationBreak> findResolvedBreaksInRange(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    /**
     * Find breaks by assigned investigator
     */
    List<ReconciliationBreak> findByAssignedInvestigatorOrderByDetectedAtDesc(String assignedInvestigator);

    /**
     * Get break resolution statistics
     */
    @Query("SELECT rb.resolutionMethod, COUNT(rb), AVG(TIMESTAMPDIFF(MINUTE, rb.detectedAt, rb.resolvedAt)) FROM ReconciliationBreak rb WHERE rb.status = 'RESOLVED' AND rb.detectedAt BETWEEN :startTime AND :endTime GROUP BY rb.resolutionMethod")
    List<Object[]> getBreakResolutionStatistics(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    /**
     * Find similar breaks for pattern analysis
     */
    @Query("SELECT rb FROM ReconciliationBreak rb WHERE rb.breakType = :breakType AND rb.entityId = :entityId AND rb.status NOT IN ('RESOLVED', 'CLOSED') AND rb.breakId != :excludeBreakId ORDER BY rb.detectedAt DESC")
    List<ReconciliationBreak> findSimilarBreaks(
        @Param("breakType") ReconciliationBreak.BreakType breakType,
        @Param("entityId") String entityId,
        @Param("excludeBreakId") UUID excludeBreakId
    );

    /**
     * Find breaks for escalation (old unresolved breaks)
     */
    @Query("SELECT rb FROM ReconciliationBreak rb WHERE rb.status IN ('NEW', 'INVESTIGATING') AND rb.detectedAt < :escalationTime ORDER BY rb.detectedAt ASC")
    List<ReconciliationBreak> findBreaksForEscalation(@Param("escalationTime") LocalDateTime escalationTime);

    /**
     * Delete old resolved breaks (for cleanup)
     */
    @Query("DELETE FROM ReconciliationBreak rb WHERE rb.status = 'RESOLVED' AND rb.resolvedAt < :cutoffDate")
    int deleteOldResolvedBreaks(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Count unresolved breaks by severity
     */
    @Query("SELECT rb.severity, COUNT(rb) FROM ReconciliationBreak rb WHERE rb.status NOT IN ('RESOLVED', 'CLOSED') GROUP BY rb.severity")
    List<Object[]> countUnresolvedBreaksBySeverity();

    /**
     * Find breaks with specific variance amount range
     */
    @Query("SELECT rb FROM ReconciliationBreak rb JOIN rb.variances v WHERE v.amount BETWEEN :minAmount AND :maxAmount ORDER BY rb.detectedAt DESC")
    List<ReconciliationBreak> findBreaksByVarianceAmountRange(
        @Param("minAmount") java.math.BigDecimal minAmount,
        @Param("maxAmount") java.math.BigDecimal maxAmount
    );

    /**
     * Find breaks that were auto-resolved
     */
    @Query("SELECT rb FROM ReconciliationBreak rb WHERE rb.resolutionMethod LIKE '%AUTOMATIC%' AND rb.resolvedAt BETWEEN :startTime AND :endTime ORDER BY rb.resolvedAt DESC")
    List<ReconciliationBreak> findAutoResolvedBreaks(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
}