package com.waqiti.common.repository;

import com.waqiti.common.model.alert.AlertSeverity;
import com.waqiti.common.model.alert.AlertStatus;
import com.waqiti.common.model.alert.SystemAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Production-grade repository for system alerts with optimized queries.
 */
@Repository
public interface SystemAlertRepository extends JpaRepository<SystemAlert, String> {

    // Basic queries
    List<SystemAlert> findBySourceService(String sourceService);
    List<SystemAlert> findByAlertType(String alertType);
    List<SystemAlert> findByCorrelationId(String correlationId);
    List<SystemAlert> findByStatus(AlertStatus status);
    List<SystemAlert> findBySeverity(AlertSeverity severity);

    // Compound queries with optimization
    @Query("SELECT a FROM SystemAlert a WHERE a.status = :status AND a.severity = :severity ORDER BY a.createdAt DESC")
    List<SystemAlert> findByStatusAndSeverity(@Param("status") AlertStatus status, @Param("severity") AlertSeverity severity);

    @Query("SELECT a FROM SystemAlert a WHERE a.status IN :statuses AND a.severity IN :severities ORDER BY a.severity DESC, a.createdAt DESC")
    List<SystemAlert> findByStatusInAndSeverityIn(@Param("statuses") List<AlertStatus> statuses, @Param("severities") List<AlertSeverity> severities);

    // Active alerts
    @Query("SELECT a FROM SystemAlert a WHERE a.status IN ('ACTIVE', 'ACKNOWLEDGED', 'IN_PROGRESS', 'ESCALATED') ORDER BY a.severity DESC, a.createdAt DESC")
    List<SystemAlert> findAllActive();

    @Query("SELECT a FROM SystemAlert a WHERE a.status IN ('ACTIVE', 'ACKNOWLEDGED', 'IN_PROGRESS', 'ESCALATED') AND a.sourceService = :service ORDER BY a.severity DESC, a.createdAt DESC")
    List<SystemAlert> findActiveByService(@Param("service") String service);

    // Critical alerts
    @Query("SELECT a FROM SystemAlert a WHERE a.severity IN ('CRITICAL', 'EMERGENCY') AND a.status IN ('ACTIVE', 'ACKNOWLEDGED', 'IN_PROGRESS') ORDER BY a.createdAt DESC")
    List<SystemAlert> findActiveCriticalAlerts();

    // Time-based queries
    @Query("SELECT a FROM SystemAlert a WHERE a.createdAt > :since ORDER BY a.createdAt DESC")
    List<SystemAlert> findRecentAlerts(@Param("since") Instant since);

    @Query("SELECT a FROM SystemAlert a WHERE a.createdAt BETWEEN :start AND :end ORDER BY a.createdAt DESC")
    List<SystemAlert> findAlertsBetween(@Param("start") Instant start, @Param("end") Instant end);

    // Escalation queries
    @Query("SELECT a FROM SystemAlert a WHERE a.escalationLevel >= :level ORDER BY a.escalationLevel DESC, a.createdAt DESC")
    List<SystemAlert> findByEscalationLevelGreaterThanEqual(@Param("level") int level);

    // Stale alerts (active for too long)
    @Query("SELECT a FROM SystemAlert a WHERE a.status IN ('ACTIVE', 'ACKNOWLEDGED') AND a.createdAt < :staleTime ORDER BY a.createdAt ASC")
    List<SystemAlert> findStaleAlerts(@Param("staleTime") Instant staleTime);

    // Assignment queries
    List<SystemAlert> findByAssignedTo(String assignedTo);

    @Query("SELECT a FROM SystemAlert a WHERE a.assignedTo = :assignedTo AND a.status IN ('ACTIVE', 'ACKNOWLEDGED', 'IN_PROGRESS') ORDER BY a.severity DESC, a.createdAt DESC")
    List<SystemAlert> findActiveAlertsAssignedTo(@Param("assignedTo") String assignedTo);

    // Statistics queries
    @Query("SELECT a.alertType, COUNT(a) FROM SystemAlert a WHERE a.createdAt > :since GROUP BY a.alertType ORDER BY COUNT(a) DESC")
    List<Object[]> countByAlertTypeSince(@Param("since") Instant since);

    @Query("SELECT a.severity, COUNT(a) FROM SystemAlert a WHERE a.status IN ('ACTIVE', 'ACKNOWLEDGED', 'IN_PROGRESS') GROUP BY a.severity")
    List<Object[]> countActiveBySeverity();

    @Query("SELECT a.sourceService, a.severity, COUNT(a) FROM SystemAlert a WHERE a.status IN ('ACTIVE', 'ACKNOWLEDGED', 'IN_PROGRESS') GROUP BY a.sourceService, a.severity ORDER BY COUNT(a) DESC")
    List<Object[]> countActiveByServiceAndSeverity();

    // Count queries for metrics
    long countByStatus(AlertStatus status);
    long countBySeverity(AlertSeverity severity);

    @Query("SELECT COUNT(a) FROM SystemAlert a WHERE a.status IN ('ACTIVE', 'ACKNOWLEDGED', 'IN_PROGRESS', 'ESCALATED')")
    long countActive();

    @Query("SELECT COUNT(a) FROM SystemAlert a WHERE a.severity IN ('CRITICAL', 'EMERGENCY') AND a.status IN ('ACTIVE', 'ACKNOWLEDGED', 'IN_PROGRESS')")
    long countActiveCritical();

    // Cleanup
    @Query("SELECT a FROM SystemAlert a WHERE a.status = 'CLOSED' AND a.lastUpdated < :cutoff")
    List<SystemAlert> findClosedAlertsOlderThan(@Param("cutoff") Instant cutoff);
}
