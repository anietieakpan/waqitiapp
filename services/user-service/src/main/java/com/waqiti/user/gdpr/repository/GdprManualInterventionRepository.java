package com.waqiti.user.gdpr.repository;

import com.waqiti.user.gdpr.entity.GdprManualIntervention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for GDPR Manual Interventions
 * Supports SLA monitoring and compliance tracking
 */
@Repository
public interface GdprManualInterventionRepository extends JpaRepository<GdprManualIntervention, UUID> {

    /**
     * Find intervention by ticket number
     */
    Optional<GdprManualIntervention> findByTicketNumber(String ticketNumber);

    /**
     * Find all interventions for a user
     */
    List<GdprManualIntervention> findByUserId(UUID userId);

    /**
     * Find interventions by status
     */
    List<GdprManualIntervention> findByStatus(String status);

    /**
     * Find interventions approaching SLA deadline
     */
    @Query("SELECT g FROM GdprManualIntervention g WHERE g.slaDeadline < :deadline " +
           "AND g.status IN ('PENDING', 'IN_PROGRESS') ORDER BY g.slaDeadline ASC")
    List<GdprManualIntervention> findBySlaDeadlineBefore(@Param("deadline") LocalDateTime deadline);

    /**
     * Count interventions by status
     */
    long countByStatus(String status);

    /**
     * Count overdue interventions (SLA breached)
     */
    @Query("SELECT COUNT(g) FROM GdprManualIntervention g WHERE g.slaDeadline < :now " +
           "AND g.status IN ('PENDING', 'IN_PROGRESS', 'ESCALATED')")
    long countOverdueInterventions(@Param("now") LocalDateTime now);

    /**
     * Find interventions by operation type
     */
    List<GdprManualIntervention> findByOperationType(String operationType);

    /**
     * Find interventions assigned to specific operator
     */
    List<GdprManualIntervention> findByAssignedTo(String assignedTo);

    /**
     * Find unassigned pending interventions
     */
    @Query("SELECT g FROM GdprManualIntervention g WHERE g.status = 'PENDING' " +
           "AND g.assignedTo IS NULL ORDER BY g.slaDeadline ASC")
    List<GdprManualIntervention> findUnassignedPending();

    /**
     * Get SLA compliance statistics
     */
    @Query("SELECT " +
           "COUNT(CASE WHEN g.resolvedAt <= g.slaDeadline THEN 1 END) as withinSla, " +
           "COUNT(CASE WHEN g.resolvedAt > g.slaDeadline THEN 1 END) as breachedSla, " +
           "COUNT(CASE WHEN g.status != 'RESOLVED' AND CURRENT_TIMESTAMP > g.slaDeadline THEN 1 END) as currentlyOverdue " +
           "FROM GdprManualIntervention g")
    Object[] getSlaStatistics();
}
