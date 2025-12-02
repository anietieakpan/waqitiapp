package com.waqiti.wallet.repository;

import com.waqiti.wallet.domain.RegulatoryIncident;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Regulatory Incident Repository
 *
 * MongoDB repository for regulatory incident management and tracking.
 * Supports queries for incident lifecycle, reporting deadlines, and
 * regulatory authority coordination.
 *
 * @author Waqiti Compliance Team
 * @since 1.0
 */
@Repository
public interface RegulatoryIncidentRepository extends MongoRepository<RegulatoryIncident, String> {

    /**
     * Find all incidents for a specific user
     */
    List<RegulatoryIncident> findByUserId(UUID userId);

    /**
     * Find incidents by status
     */
    List<RegulatoryIncident> findByStatus(String status);

    /**
     * Find incidents by multiple statuses
     */
    List<RegulatoryIncident> findByStatusIn(List<String> statuses);

    /**
     * Find incidents requiring regulator notification
     */
    List<RegulatoryIncident> findByRequiresRegulatorNotificationTrue();

    /**
     * Find overdue incidents (past reporting deadline and not yet reported)
     */
    @Query("{ 'reportingDeadline': { $lt: ?0 }, 'status': { $nin: ['REPORTED', 'RESOLVED', 'CLOSED'] } }")
    List<RegulatoryIncident> findOverdueIncidents(LocalDateTime now);

    /**
     * Find incidents by case ID
     */
    List<RegulatoryIncident> findByCaseId(String caseId);

    /**
     * Find incidents by severity
     */
    List<RegulatoryIncident> findBySeverity(String severity);

    /**
     * Find incidents by regulatory authority
     */
    List<RegulatoryIncident> findByRegulatoryAuthority(String authority);

    /**
     * Find recent incidents (created within time period)
     */
    @Query("{ 'createdAt': { $gte: ?0 } }")
    List<RegulatoryIncident> findRecentIncidents(LocalDateTime since);

    /**
     * Find incidents approaching deadline (within next N days)
     */
    @Query("{ 'reportingDeadline': { $lte: ?0, $gte: ?1 }, 'status': { $nin: ['REPORTED', 'RESOLVED', 'CLOSED'] } }")
    List<RegulatoryIncident> findIncidentsApproachingDeadline(LocalDateTime upperBound, LocalDateTime now);

    /**
     * Count incidents by status
     */
    long countByStatus(String status);

    /**
     * Count incidents by multiple statuses
     */
    long countByStatusIn(List<String> statuses);

    /**
     * Count overdue incidents
     */
    @Query(value = "{ 'reportingDeadline': { $lt: ?0 }, 'status': { $nin: ['REPORTED', 'RESOLVED', 'CLOSED'] } }",
           count = true)
    long countOverdueIncidents(LocalDateTime now);

    /**
     * Count incidents requiring regulator notification
     */
    long countByRequiresRegulatorNotificationTrue();
}
