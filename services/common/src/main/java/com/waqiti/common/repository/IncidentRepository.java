package com.waqiti.common.repository;

import com.waqiti.common.model.incident.Incident;
import com.waqiti.common.model.incident.IncidentPriority;
import com.waqiti.common.model.incident.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Production-grade repository for incidents with optimized queries for P0/P1 workflows.
 */
@Repository
public interface IncidentRepository extends JpaRepository<Incident, String> {

    // Basic queries
    List<Incident> findByPriority(IncidentPriority priority);
    List<Incident> findByStatus(IncidentStatus status);
    List<Incident> findBySourceService(String sourceService);
    List<Incident> findByAssignedTo(String assignedTo);
    List<Incident> findByCorrelationId(String correlationId);

    // Active incidents
    @Query("SELECT i FROM Incident i WHERE i.status IN ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS') ORDER BY i.priority, i.createdAt")
    List<Incident> findAllActive();

    @Query("SELECT i FROM Incident i WHERE i.status IN ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS') AND i.priority IN :priorities ORDER BY i.priority, i.createdAt")
    List<Incident> findActiveByPriorities(@Param("priorities") List<IncidentPriority> priorities);

    // Critical incidents (P0/P1)
    @Query("SELECT i FROM Incident i WHERE i.priority IN ('P0', 'P1') AND i.status IN ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS') ORDER BY i.priority, i.createdAt")
    List<Incident> findActiveCritical();

    // Unacknowledged incidents
    @Query("SELECT i FROM Incident i WHERE i.status = 'OPEN' AND i.acknowledgedAt IS NULL ORDER BY i.priority, i.createdAt")
    List<Incident> findUnacknowledged();

    @Query("SELECT i FROM Incident i WHERE i.status = 'OPEN' AND i.acknowledgedAt IS NULL AND i.priority IN ('P0', 'P1') ORDER BY i.priority, i.createdAt")
    List<Incident> findUnacknowledgedCritical();

    // SLA tracking
    @Query("SELECT i FROM Incident i WHERE i.slaDeadline < :now AND i.status IN ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS') ORDER BY i.slaDeadline")
    List<Incident> findSlaBreached(@Param("now") Instant now);

    @Query("SELECT i FROM Incident i WHERE i.slaDeadline BETWEEN :now AND :soon AND i.status IN ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS') ORDER BY i.slaDeadline")
    List<Incident> findApproachingSla(@Param("now") Instant now, @Param("soon") Instant soon);

    @Query("SELECT i FROM Incident i WHERE i.slaBreached = true")
    List<Incident> findAllSlaBreached();

    // Assignment queries
    @Query("SELECT i FROM Incident i WHERE i.assignedTo = :assignedTo AND i.status IN ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS') ORDER BY i.priority, i.createdAt")
    List<Incident> findActiveAssignedTo(@Param("assignedTo") String assignedTo);

    @Query("SELECT i FROM Incident i WHERE i.assignedTo IS NULL AND i.status IN ('OPEN', 'ACKNOWLEDGED') ORDER BY i.priority, i.createdAt")
    List<Incident> findUnassigned();

    // Escalation queries
    @Query("SELECT i FROM Incident i WHERE i.escalationLevel >= :level ORDER BY i.escalationLevel DESC, i.createdAt")
    List<Incident> findByEscalationLevelGreaterThanEqual(@Param("level") int level);

    @Query("SELECT i FROM Incident i WHERE i.escalatedAt IS NOT NULL AND i.status IN ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS') ORDER BY i.escalationLevel DESC, i.escalatedAt DESC")
    List<Incident> findAllEscalated();

    // Related incidents
    @Query("SELECT i FROM Incident i WHERE i.parentIncidentId = :parentId")
    List<Incident> findChildIncidents(@Param("parentId") String parentId);

    // Time-based queries
    @Query("SELECT i FROM Incident i WHERE i.createdAt > :since ORDER BY i.createdAt DESC")
    List<Incident> findRecentIncidents(@Param("since") Instant since);

    @Query("SELECT i FROM Incident i WHERE i.createdAt BETWEEN :start AND :end ORDER BY i.createdAt DESC")
    List<Incident> findIncidentsBetween(@Param("start") Instant start, @Param("end") Instant end);

    // Stale incidents (open too long)
    @Query("SELECT i FROM Incident i WHERE i.status IN ('OPEN', 'ACKNOWLEDGED') AND i.createdAt < :staleTime ORDER BY i.createdAt ASC")
    List<Incident> findStaleIncidents(@Param("staleTime") Instant staleTime);

    // Statistics
    @Query("SELECT i.priority, COUNT(i) FROM Incident i WHERE i.status IN ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS') GROUP BY i.priority ORDER BY i.priority")
    List<Object[]> countActiveByPriority();

    @Query("SELECT i.sourceService, COUNT(i) FROM Incident i WHERE i.status IN ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS') GROUP BY i.sourceService ORDER BY COUNT(i) DESC")
    List<Object[]> countActiveByService();

    @Query("SELECT i.assignedTo, COUNT(i) FROM Incident i WHERE i.assignedTo IS NOT NULL AND i.status IN ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS') GROUP BY i.assignedTo ORDER BY COUNT(i) DESC")
    List<Object[]> countActiveByAssignee();

    // Count queries for dashboards
    long countByStatus(IncidentStatus status);
    long countByPriority(IncidentPriority priority);

    @Query("SELECT COUNT(i) FROM Incident i WHERE i.status IN ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS')")
    long countActive();

    @Query("SELECT COUNT(i) FROM Incident i WHERE i.priority IN ('P0', 'P1') AND i.status IN ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS')")
    long countActiveCritical();

    @Query("SELECT COUNT(i) FROM Incident i WHERE i.slaBreached = true AND i.status IN ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS')")
    long countActiveSlaBreached();

    // Service-specific queries
    @Query("SELECT i FROM Incident i WHERE i.sourceService = :service AND i.status IN ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS') ORDER BY i.priority, i.createdAt")
    List<Incident> findActiveByService(@Param("service") String service);

    @Query("SELECT i FROM Incident i WHERE i.sourceService = :service AND i.priority IN ('P0', 'P1') AND i.status IN ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS') ORDER BY i.priority, i.createdAt")
    List<Incident> findActiveCriticalByService(@Param("service") String service);

    // Status-based queries
    List<Incident> findByStatusIn(List<IncidentStatus> statuses);
}
