package com.waqiti.compliance.repository;

import com.waqiti.compliance.entity.IncidentEscalation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Incident Escalation Repository
 *
 * Data access layer for incident escalations with custom queries
 * for escalation chain tracking and audit compliance.
 *
 * @author Waqiti Compliance Team
 * @version 1.0
 */
@Repository
public interface IncidentEscalationRepository extends JpaRepository<IncidentEscalation, String> {

    /**
     * Find all escalations for an incident
     *
     * @param incidentId incident ID
     * @return list of escalations ordered by escalation time
     */
    List<IncidentEscalation> findByIncidentIdOrderByEscalatedAtAsc(String incidentId);

    /**
     * Find all escalations to a specific target
     *
     * @param escalatedTo escalation target (role or user ID)
     * @return list of escalations
     */
    List<IncidentEscalation> findByEscalatedToOrderByEscalatedAtDesc(String escalatedTo);

    /**
     * Find all escalations from a specific source
     *
     * @param escalatedFrom escalation source (role or user ID)
     * @return list of escalations
     */
    List<IncidentEscalation> findByEscalatedFromOrderByEscalatedAtDesc(String escalatedFrom);

    /**
     * Find all escalations by specific user
     *
     * @param escalatedBy user who performed escalation
     * @return list of escalations
     */
    List<IncidentEscalation> findByEscalatedByOrderByEscalatedAtDesc(String escalatedBy);

    /**
     * Find all executive escalations (C-suite)
     *
     * @return list of executive escalations
     */
    @Query("SELECT e FROM IncidentEscalation e WHERE e.escalatedTo LIKE '%CHIEF_%' OR e.escalatedTo LIKE '%CEO%' OR e.escalatedTo LIKE '%CFO%' OR e.escalatedTo LIKE '%CTO%' ORDER BY e.escalatedAt DESC")
    List<IncidentEscalation> findExecutiveEscalations();

    /**
     * Find all auto-escalations
     *
     * @return list of auto-escalations
     */
    @Query("SELECT e FROM IncidentEscalation e WHERE e.reason LIKE 'AUTO_ESCALATION:%' ORDER BY e.escalatedAt DESC")
    List<IncidentEscalation> findAutoEscalations();

    /**
     * Find escalations within date range
     *
     * @param startDate start date
     * @param endDate end date
     * @return list of escalations
     */
    @Query("SELECT e FROM IncidentEscalation e WHERE e.escalatedAt BETWEEN :startDate AND :endDate ORDER BY e.escalatedAt DESC")
    List<IncidentEscalation> findEscalationsByDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Count escalations by incident
     *
     * @param incidentId incident ID
     * @return count of escalations
     */
    long countByIncidentId(String incidentId);

    /**
     * Count escalations to target
     *
     * @param escalatedTo escalation target
     * @return count of escalations
     */
    long countByEscalatedTo(String escalatedTo);

    /**
     * Find latest escalation for incident
     *
     * @param incidentId incident ID
     * @return latest escalation
     */
    @Query("SELECT e FROM IncidentEscalation e WHERE e.incidentId = :incidentId ORDER BY e.escalatedAt DESC LIMIT 1")
    IncidentEscalation findLatestEscalationForIncident(@Param("incidentId") String incidentId);

    /**
     * Find escalation chain for incident
     *
     * @param incidentId incident ID
     * @return escalation chain ordered by time
     */
    @Query("SELECT e FROM IncidentEscalation e WHERE e.incidentId = :incidentId ORDER BY e.escalatedAt ASC")
    List<IncidentEscalation> findEscalationChain(@Param("incidentId") String incidentId);

    /**
     * Find all recent escalations (last 24 hours)
     *
     * @param since timestamp to search from
     * @return list of recent escalations
     */
    @Query("SELECT e FROM IncidentEscalation e WHERE e.escalatedAt >= :since ORDER BY e.escalatedAt DESC")
    List<IncidentEscalation> findRecentEscalations(@Param("since") LocalDateTime since);
}
