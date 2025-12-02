package com.waqiti.compliance.repository;

import com.waqiti.compliance.entity.IncidentTimelineEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Incident Timeline Repository
 *
 * Data access layer for incident timeline entries providing
 * complete chronological audit trail for compliance and investigations.
 *
 * @author Waqiti Compliance Team
 * @version 1.0
 */
@Repository
public interface IncidentTimelineRepository extends JpaRepository<IncidentTimelineEntry, String> {

    /**
     * Find all timeline entries for an incident
     *
     * @param incidentId incident ID
     * @return list of timeline entries ordered chronologically
     */
    List<IncidentTimelineEntry> findByIncidentIdOrderByCreatedAtAsc(String incidentId);

    /**
     * Find all timeline entries by event type
     *
     * @param eventType event type
     * @return list of timeline entries
     */
    List<IncidentTimelineEntry> findByEventTypeOrderByCreatedAtDesc(String eventType);

    /**
     * Find all timeline entries created by user
     *
     * @param createdBy user ID
     * @return list of timeline entries
     */
    List<IncidentTimelineEntry> findByCreatedByOrderByCreatedAtDesc(String createdBy);

    /**
     * Find all system-generated timeline entries
     *
     * @return list of system entries
     */
    @Query("SELECT t FROM IncidentTimelineEntry t WHERE t.createdBy = 'SYSTEM' OR t.eventType LIKE 'AUTO_%' OR t.eventType LIKE '%_AUTOMATED_%' ORDER BY t.createdAt DESC")
    List<IncidentTimelineEntry> findSystemGeneratedEntries();

    /**
     * Find all critical timeline entries
     *
     * @return list of critical entries
     */
    @Query("SELECT t FROM IncidentTimelineEntry t WHERE t.eventType LIKE '%ESCALAT%' OR t.eventType LIKE '%BREACH%' OR t.eventType LIKE '%EMERGENCY%' OR t.eventType LIKE '%CRITICAL%' ORDER BY t.createdAt DESC")
    List<IncidentTimelineEntry> findCriticalEntries();

    /**
     * Find timeline entries for incident within date range
     *
     * @param incidentId incident ID
     * @param startDate start date
     * @param endDate end date
     * @return list of timeline entries
     */
    @Query("SELECT t FROM IncidentTimelineEntry t WHERE t.incidentId = :incidentId AND t.createdAt BETWEEN :startDate AND :endDate ORDER BY t.createdAt ASC")
    List<IncidentTimelineEntry> findByIncidentAndDateRange(
        @Param("incidentId") String incidentId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find all timeline entries within date range
     *
     * @param startDate start date
     * @param endDate end date
     * @return list of timeline entries
     */
    @Query("SELECT t FROM IncidentTimelineEntry t WHERE t.createdAt BETWEEN :startDate AND :endDate ORDER BY t.createdAt DESC")
    List<IncidentTimelineEntry> findByDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Count timeline entries for incident
     *
     * @param incidentId incident ID
     * @return count of entries
     */
    long countByIncidentId(String incidentId);

    /**
     * Count timeline entries by event type
     *
     * @param eventType event type
     * @return count of entries
     */
    long countByEventType(String eventType);

    /**
     * Find latest timeline entry for incident
     *
     * @param incidentId incident ID
     * @return latest timeline entry
     */
    @Query("SELECT t FROM IncidentTimelineEntry t WHERE t.incidentId = :incidentId ORDER BY t.createdAt DESC LIMIT 1")
    IncidentTimelineEntry findLatestEntryForIncident(@Param("incidentId") String incidentId);

    /**
     * Find first timeline entry for incident
     *
     * @param incidentId incident ID
     * @return first timeline entry
     */
    @Query("SELECT t FROM IncidentTimelineEntry t WHERE t.incidentId = :incidentId ORDER BY t.createdAt ASC LIMIT 1")
    IncidentTimelineEntry findFirstEntryForIncident(@Param("incidentId") String incidentId);

    /**
     * Find all timeline entries for multiple incidents
     *
     * @param incidentIds list of incident IDs
     * @return list of timeline entries
     */
    @Query("SELECT t FROM IncidentTimelineEntry t WHERE t.incidentId IN :incidentIds ORDER BY t.incidentId, t.createdAt ASC")
    List<IncidentTimelineEntry> findByIncidentIdIn(@Param("incidentIds") List<String> incidentIds);

    /**
     * Find recent timeline entries (last 24 hours)
     *
     * @param since timestamp to search from
     * @return list of recent entries
     */
    @Query("SELECT t FROM IncidentTimelineEntry t WHERE t.createdAt >= :since ORDER BY t.createdAt DESC")
    List<IncidentTimelineEntry> findRecentEntries(@Param("since") LocalDateTime since);
}
