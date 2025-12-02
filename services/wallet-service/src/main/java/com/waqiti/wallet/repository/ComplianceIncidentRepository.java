package com.waqiti.wallet.repository;

import com.waqiti.wallet.domain.ComplianceIncident;
import com.waqiti.wallet.domain.ComplianceIncidentStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Compliance Incident Repository
 *
 * MongoDB repository for compliance incident management and tracking.
 * Provides queries for incident lifecycle management, SLA tracking,
 * and regulatory reporting.
 *
 * @author Waqiti Compliance Team
 * @since 1.0
 */
@Repository
public interface ComplianceIncidentRepository extends MongoRepository<ComplianceIncident, String> {

    /**
     * Find all incidents for a specific user
     */
    List<ComplianceIncident> findByUserId(UUID userId);

    /**
     * Find incidents by status
     */
    List<ComplianceIncident> findByStatus(ComplianceIncidentStatus status);

    /**
     * Find incidents requiring immediate review
     */
    List<ComplianceIncident> findByStatusOrderByCreatedAtAsc(ComplianceIncidentStatus status);

    /**
     * Find overdue incidents (past due date and not resolved)
     */
    @Query("{ 'dueDate': { $lt: ?0 }, 'status': { $nin: ['RESOLVED', 'CLOSED', 'CANCELLED'] } }")
    List<ComplianceIncident> findOverdueIncidents(LocalDateTime now);

    /**
     * Find critical incidents requiring immediate action
     */
    @Query("{ 'severity': { $in: ['CRITICAL_REGULATORY', 'HIGH_REGULATORY'] }, " +
           "'status': { $nin: ['RESOLVED', 'CLOSED', 'CANCELLED'] } }")
    List<ComplianceIncident> findCriticalIncidents();

    /**
     * Find regulatory-reportable incidents
     */
    List<ComplianceIncident> findByIsRegulatoryReportableTrue();

    /**
     * Find incidents by case ID
     */
    List<ComplianceIncident> findByCaseId(String caseId);

    /**
     * Find recent incidents (created within last N hours)
     */
    @Query("{ 'createdAt': { $gte: ?0 } }")
    List<ComplianceIncident> findRecentIncidents(LocalDateTime since);

    /**
     * Count incidents by status
     */
    long countByStatus(ComplianceIncidentStatus status);

    /**
     * Count overdue incidents
     */
    @Query(value = "{ 'dueDate': { $lt: ?0 }, 'status': { $nin: ['RESOLVED', 'CLOSED', 'CANCELLED'] } }",
           count = true)
    long countOverdueIncidents(LocalDateTime now);
}
