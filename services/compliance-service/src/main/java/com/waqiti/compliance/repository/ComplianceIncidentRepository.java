package com.waqiti.compliance.repository;

import com.waqiti.compliance.entity.ComplianceIncident;
import com.waqiti.compliance.enums.IncidentSeverity;
import com.waqiti.compliance.enums.IncidentStatus;
import com.waqiti.compliance.enums.IncidentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Compliance Incident Repository
 *
 * Data access layer for compliance incidents with custom queries
 * for SLA tracking, escalation management, and regulatory reporting.
 *
 * Features pessimistic locking for concurrent update scenarios.
 *
 * @author Waqiti Compliance Team
 * @version 1.0
 */
@Repository
public interface ComplianceIncidentRepository extends JpaRepository<ComplianceIncident, String> {

    /**
     * Find incident by ID with pessimistic write lock
     * Use this when updating incident to prevent concurrent modifications
     *
     * @param incidentId incident ID
     * @return optional incident with write lock
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM ComplianceIncident i WHERE i.incidentId = :incidentId")
    Optional<ComplianceIncident> findByIdWithLock(@Param("incidentId") String incidentId);

    /**
     * Find all incidents by status
     *
     * @param status incident status
     * @return list of incidents
     */
    List<ComplianceIncident> findByStatus(IncidentStatus status);

    /**
     * Find all incidents by incident type
     *
     * @param incidentType incident type
     * @return list of incidents
     */
    List<ComplianceIncident> findByIncidentType(IncidentType incidentType);

    /**
     * Find all incidents by severity
     *
     * @param severity incident severity
     * @return list of incidents
     */
    List<ComplianceIncident> findBySeverity(IncidentSeverity severity);

    /**
     * Find all incidents assigned to user
     *
     * @param assignedTo user ID
     * @return list of incidents
     */
    List<ComplianceIncident> findByAssignedToOrderByCreatedAtDesc(String assignedTo);

    /**
     * Find all incidents by user ID
     *
     * @param userId user ID
     * @return list of incidents
     */
    List<ComplianceIncident> findByUserId(String userId);

    /**
     * Find all active incidents (not resolved or closed)
     *
     * @return list of active incidents
     */
    @Query("SELECT i FROM ComplianceIncident i WHERE i.status NOT IN ('RESOLVED', 'CLOSED') ORDER BY i.severity DESC, i.createdAt ASC")
    List<ComplianceIncident> findActiveIncidents();

    /**
     * Find all SLA breached incidents
     *
     * @return list of SLA breached incidents
     */
    @Query("SELECT i FROM ComplianceIncident i WHERE i.slaBreached = true AND i.status NOT IN ('RESOLVED', 'CLOSED')")
    List<ComplianceIncident> findSLABreachedIncidents();

    /**
     * Find all incidents approaching SLA breach
     *
     * @param thresholdTime threshold time (e.g., 1 hour before SLA)
     * @return list of incidents approaching SLA breach
     */
    @Query("SELECT i FROM ComplianceIncident i WHERE i.slaDeadline < :thresholdTime AND i.slaBreached = false AND i.status NOT IN ('RESOLVED', 'CLOSED')")
    List<ComplianceIncident> findIncidentsApproachingSLABreach(@Param("thresholdTime") LocalDateTime thresholdTime);

    /**
     * Find all critical incidents
     *
     * @return list of critical incidents
     */
    @Query("SELECT i FROM ComplianceIncident i WHERE i.severity = 'CRITICAL' AND i.status NOT IN ('RESOLVED', 'CLOSED') ORDER BY i.createdAt ASC")
    List<ComplianceIncident> findCriticalIncidents();

    /**
     * Find all incidents requiring escalation
     *
     * @return list of incidents requiring escalation
     */
    @Query("SELECT i FROM ComplianceIncident i WHERE i.status = 'ESCALATED'")
    List<ComplianceIncident> findEscalatedIncidents();

    /**
     * Find all incidents requiring regulatory reporting
     *
     * @return list of incidents requiring reporting
     */
    @Query("SELECT i FROM ComplianceIncident i WHERE i.regulatoryReportingRequired = true AND i.status NOT IN ('CLOSED')")
    List<ComplianceIncident> findIncidentsRequiringRegulatoryReporting();

    /**
     * Find incidents created within date range
     *
     * @param startDate start date
     * @param endDate end date
     * @return list of incidents
     */
    @Query("SELECT i FROM ComplianceIncident i WHERE i.createdAt BETWEEN :startDate AND :endDate ORDER BY i.createdAt DESC")
    List<ComplianceIncident> findIncidentsByDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find incidents resolved within date range
     *
     * @param startDate start date
     * @param endDate end date
     * @return list of incidents
     */
    @Query("SELECT i FROM ComplianceIncident i WHERE i.resolvedAt BETWEEN :startDate AND :endDate ORDER BY i.resolvedAt DESC")
    List<ComplianceIncident> findIncidentsResolvedInDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Count incidents by status
     *
     * @param status incident status
     * @return count of incidents
     */
    long countByStatus(IncidentStatus status);

    /**
     * Count incidents by severity
     *
     * @param severity incident severity
     * @return count of incidents
     */
    long countBySeverity(IncidentSeverity severity);

    /**
     * Count active incidents by user
     *
     * @param userId user ID
     * @return count of active incidents
     */
    @Query("SELECT COUNT(i) FROM ComplianceIncident i WHERE i.userId = :userId AND i.status NOT IN ('RESOLVED', 'CLOSED')")
    long countActiveIncidentsByUser(@Param("userId") String userId);

    /**
     * Count SLA breached incidents
     *
     * @return count of SLA breached incidents
     */
    @Query("SELECT COUNT(i) FROM ComplianceIncident i WHERE i.slaBreached = true AND i.status NOT IN ('RESOLVED', 'CLOSED')")
    long countSLABreachedIncidents();

    /**
     * Find incidents by priority
     *
     * @param priority priority (e.g., "P0", "P1")
     * @return list of incidents
     */
    @Query("SELECT i FROM ComplianceIncident i WHERE i.priority = :priority AND i.status NOT IN ('RESOLVED', 'CLOSED') ORDER BY i.createdAt ASC")
    List<ComplianceIncident> findByPriority(@Param("priority") String priority);

    /**
     * Find all incidents linked to investigation
     *
     * @param investigationId investigation ID
     * @return list of incidents
     */
    @Query(value = "SELECT * FROM compliance_incidents WHERE linked_investigations::jsonb @> :investigationId::jsonb", nativeQuery = true)
    List<ComplianceIncident> findByLinkedInvestigation(@Param("investigationId") String investigationId);
}
