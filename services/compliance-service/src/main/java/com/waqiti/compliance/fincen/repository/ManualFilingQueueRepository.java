package com.waqiti.compliance.fincen.repository;

import com.waqiti.compliance.fincen.entity.FilingPriority;
import com.waqiti.compliance.fincen.entity.FilingStatus;
import com.waqiti.compliance.fincen.entity.ManualFilingQueueEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Manual Filing Queue
 *
 * @author Waqiti Compliance Team
 * @version 2.0.0
 * @since 2025-11-04
 */
@Repository
public interface ManualFilingQueueRepository extends JpaRepository<ManualFilingQueueEntry, UUID> {

    // ========== BASIC QUERIES ==========

    List<ManualFilingQueueEntry> findByStatus(FilingStatus status);

    Optional<ManualFilingQueueEntry> findBySarId(String sarId);

    List<ManualFilingQueueEntry> findByPriority(FilingPriority priority);

    // ========== COUNTING QUERIES ==========

    long countByStatus(FilingStatus status);

    long countByStatusAndPriority(FilingStatus status, FilingPriority priority);

    @Query("SELECT COUNT(e) FROM ManualFilingQueueEntry e WHERE e.status = 'MANUALLY_FILED' " +
           "AND e.filedAt >= :since")
    long countFiledInLast24Hours(@Param("since") LocalDateTime since);

    // ========== SLA & ESCALATION QUERIES ==========

    @Query("SELECT e FROM ManualFilingQueueEntry e WHERE e.status = 'PENDING' " +
           "AND e.slaDeadline <= :deadline ORDER BY e.slaDeadline ASC")
    List<ManualFilingQueueEntry> findEntriesApproachingSla(@Param("deadline") LocalDateTime deadline);

    @Query("SELECT e FROM ManualFilingQueueEntry e WHERE e.status = 'PENDING' " +
           "AND e.slaDeadline < :now AND e.escalated = false")
    List<ManualFilingQueueEntry> findOverdueUnescalated(@Param("now") LocalDateTime now);

    @Query("SELECT e FROM ManualFilingQueueEntry e WHERE e.status = 'PENDING' " +
           "ORDER BY e.queuedAt ASC")
    Optional<ManualFilingQueueEntry> findOldestPending();

    @Query("SELECT e FROM ManualFilingQueueEntry e WHERE e.status = 'OVERDUE' " +
           "OR (e.status = 'PENDING' AND e.priority = 'EXPEDITED') " +
           "ORDER BY e.slaDeadline ASC")
    List<ManualFilingQueueEntry> findCriticalEntries();

    // ========== STATISTICS QUERIES ==========

    @Query("SELECT AVG(EXTRACT(EPOCH FROM (e.filedAt - e.queuedAt))) " +
           "FROM ManualFilingQueueEntry e WHERE e.status = 'MANUALLY_FILED' " +
           "AND e.filedAt IS NOT NULL")
    Double calculateAverageTimeToFile();

    @Query("SELECT e FROM ManualFilingQueueEntry e WHERE e.queuedAt >= :since " +
           "ORDER BY e.queuedAt DESC")
    List<ManualFilingQueueEntry> findRecentEntries(@Param("since") LocalDateTime since);

    // ========== CLEANUP QUERIES ==========

    @Query("SELECT e FROM ManualFilingQueueEntry e WHERE e.status = 'MANUALLY_FILED' " +
           "AND e.filedAt < :cutoffDate")
    List<ManualFilingQueueEntry> findOldFiledEntries(@Param("cutoffDate") LocalDateTime cutoffDate);
}
