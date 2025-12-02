package com.waqiti.payment.repository;

import com.waqiti.payment.entity.ManualReviewTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * PRODUCTION REPOSITORY: Manual Review Task Management
 *
 * Provides database access for manual review tasks with:
 * - Task assignment and tracking
 * - SLA monitoring
 * - Priority-based querying
 * - Team workload queries
 * - Audit trail queries
 *
 * @author Waqiti Production Team
 * @version 2.0.0
 * @since November 18, 2025
 */
@Repository
public interface ManualReviewTaskRepository extends JpaRepository<ManualReviewTask, Long> {

    // ========================================================================
    // PRIMARY LOOKUPS
    // ========================================================================

    /**
     * Find task by entity type and ID
     */
    @Query("SELECT t FROM ManualReviewTask t WHERE t.entityType = :entityType AND t.entityId = :entityId ORDER BY t.createdAt DESC")
    List<ManualReviewTask> findByEntity(@Param("entityType") String entityType, @Param("entityId") String entityId);

    /**
     * Find tasks by payment ID
     */
    List<ManualReviewTask> findByPaymentIdOrderByCreatedAtDesc(String paymentId);

    /**
     * Find tasks by settlement ID
     */
    List<ManualReviewTask> findBySettlementIdOrderByCreatedAtDesc(String settlementId);

    /**
     * Find tasks by user ID
     */
    List<ManualReviewTask> findByUserIdOrderByCreatedAtDesc(String userId);

    // ========================================================================
    // STATUS AND PRIORITY QUERIES
    // ========================================================================

    /**
     * Find all pending tasks
     */
    @Query("SELECT t FROM ManualReviewTask t WHERE t.status = 'PENDING' ORDER BY t.priority DESC, t.createdAt ASC")
    List<ManualReviewTask> findPendingTasks();

    /**
     * Find tasks by status
     */
    @Query("SELECT t FROM ManualReviewTask t WHERE t.status = :status ORDER BY t.priority DESC, t.createdAt ASC")
    List<ManualReviewTask> findByStatus(@Param("status") ManualReviewTask.ReviewStatus status);

    /**
     * Find tasks by priority
     */
    @Query("SELECT t FROM ManualReviewTask t WHERE t.priority = :priority AND t.status IN ('PENDING', 'ASSIGNED', 'IN_PROGRESS') ORDER BY t.createdAt ASC")
    List<ManualReviewTask> findByPriority(@Param("priority") ManualReviewTask.Priority priority);

    /**
     * Find critical tasks needing immediate attention
     */
    @Query("SELECT t FROM ManualReviewTask t WHERE t.priority = 'CRITICAL' AND t.status IN ('PENDING', 'ASSIGNED', 'IN_PROGRESS') ORDER BY t.createdAt ASC")
    List<ManualReviewTask> findCriticalTasks();

    // ========================================================================
    // ASSIGNMENT QUERIES
    // ========================================================================

    /**
     * Find tasks assigned to specific user/team
     */
    @Query("SELECT t FROM ManualReviewTask t WHERE t.assignedTo = :assignee AND t.status IN ('ASSIGNED', 'IN_PROGRESS') ORDER BY t.priority DESC, t.dueDate ASC")
    List<ManualReviewTask> findByAssignedTo(@Param("assignee") String assignee);

    /**
     * Find unassigned tasks
     */
    @Query("SELECT t FROM ManualReviewTask t WHERE t.assignedTo IS NULL AND t.status = 'PENDING' ORDER BY t.priority DESC, t.createdAt ASC")
    List<ManualReviewTask> findUnassignedTasks();

    /**
     * Count tasks assigned to user/team
     */
    @Query("SELECT COUNT(t) FROM ManualReviewTask t WHERE t.assignedTo = :assignee AND t.status IN ('ASSIGNED', 'IN_PROGRESS')")
    long countByAssignedTo(@Param("assignee") String assignee);

    // ========================================================================
    // SLA MONITORING
    // ========================================================================

    /**
     * Find overdue tasks
     */
    @Query("SELECT t FROM ManualReviewTask t WHERE t.dueDate < :now AND t.status IN ('PENDING', 'ASSIGNED', 'IN_PROGRESS') ORDER BY t.priority DESC, t.dueDate ASC")
    List<ManualReviewTask> findOverdueTasks(@Param("now") LocalDateTime now);

    /**
     * Find tasks approaching SLA breach (within 1 hour)
     */
    @Query("SELECT t FROM ManualReviewTask t WHERE t.dueDate BETWEEN :now AND :warningTime AND t.status IN ('PENDING', 'ASSIGNED', 'IN_PROGRESS') ORDER BY t.dueDate ASC")
    List<ManualReviewTask> findTasksApproachingSLA(@Param("now") LocalDateTime now, @Param("warningTime") LocalDateTime warningTime);

    /**
     * Find tasks with SLA breached
     */
    @Query("SELECT t FROM ManualReviewTask t WHERE t.slaBreached = true AND t.status IN ('PENDING', 'ASSIGNED', 'IN_PROGRESS') ORDER BY t.slaBreachedAt ASC")
    List<ManualReviewTask> findSLABreachedTasks();

    /**
     * Count overdue tasks
     */
    @Query("SELECT COUNT(t) FROM ManualReviewTask t WHERE t.dueDate < :now AND t.status IN ('PENDING', 'ASSIGNED', 'IN_PROGRESS')")
    long countOverdueTasks(@Param("now") LocalDateTime now);

    // ========================================================================
    // REVIEW TYPE QUERIES
    // ========================================================================

    /**
     * Find tasks by review type
     */
    @Query("SELECT t FROM ManualReviewTask t WHERE t.reviewType = :reviewType AND t.status IN ('PENDING', 'ASSIGNED', 'IN_PROGRESS') ORDER BY t.priority DESC, t.createdAt ASC")
    List<ManualReviewTask> findByReviewType(@Param("reviewType") ManualReviewTask.ReviewType reviewType);

    /**
     * Find settlement failure tasks
     */
    @Query("SELECT t FROM ManualReviewTask t WHERE t.reviewType = 'SETTLEMENT_FAILURE' AND t.status IN ('PENDING', 'ASSIGNED', 'IN_PROGRESS') ORDER BY t.priority DESC, t.createdAt ASC")
    List<ManualReviewTask> findSettlementFailureTasks();

    /**
     * Find fraud alert tasks
     */
    @Query("SELECT t FROM ManualReviewTask t WHERE t.reviewType = 'FRAUD_ALERT' AND t.status IN ('PENDING', 'ASSIGNED', 'IN_PROGRESS') ORDER BY t.priority DESC, t.createdAt ASC")
    List<ManualReviewTask> findFraudAlertTasks();

    // ========================================================================
    // TIME-BASED QUERIES
    // ========================================================================

    /**
     * Find tasks created within date range
     */
    @Query("SELECT t FROM ManualReviewTask t WHERE t.createdAt BETWEEN :startDate AND :endDate ORDER BY t.createdAt DESC")
    List<ManualReviewTask> findByCreatedAtBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Find tasks completed within date range
     */
    @Query("SELECT t FROM ManualReviewTask t WHERE t.completedAt BETWEEN :startDate AND :endDate ORDER BY t.completedAt DESC")
    List<ManualReviewTask> findByCompletedAtBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Find tasks created today
     */
    @Query("SELECT t FROM ManualReviewTask t WHERE DATE(t.createdAt) = CURRENT_DATE ORDER BY t.priority DESC, t.createdAt ASC")
    List<ManualReviewTask> findTasksCreatedToday();

    // ========================================================================
    // ESCALATION QUERIES
    // ========================================================================

    /**
     * Find escalated tasks
     */
    @Query("SELECT t FROM ManualReviewTask t WHERE t.status = 'ESCALATED' ORDER BY t.lastEscalationAt DESC")
    List<ManualReviewTask> findEscalatedTasks();

    /**
     * Find tasks with multiple escalations
     */
    @Query("SELECT t FROM ManualReviewTask t WHERE t.escalationCount > :count AND t.status IN ('ESCALATED', 'IN_PROGRESS') ORDER BY t.escalationCount DESC, t.lastEscalationAt DESC")
    List<ManualReviewTask> findMultipleEscalations(@Param("count") int count);

    // ========================================================================
    // STATISTICS QUERIES
    // ========================================================================

    /**
     * Count tasks by status
     */
    @Query("SELECT COUNT(t) FROM ManualReviewTask t WHERE t.status = :status")
    long countByStatus(@Param("status") ManualReviewTask.ReviewStatus status);

    /**
     * Count tasks by priority
     */
    @Query("SELECT COUNT(t) FROM ManualReviewTask t WHERE t.priority = :priority AND t.status IN ('PENDING', 'ASSIGNED', 'IN_PROGRESS')")
    long countByPriority(@Param("priority") ManualReviewTask.Priority priority);

    /**
     * Count tasks by review type
     */
    @Query("SELECT COUNT(t) FROM ManualReviewTask t WHERE t.reviewType = :reviewType AND t.status IN ('PENDING', 'ASSIGNED', 'IN_PROGRESS')")
    long countByReviewType(@Param("reviewType") ManualReviewTask.ReviewType reviewType);

    /**
     * Calculate average resolution time
     */
    @Query("SELECT AVG(TIMESTAMPDIFF(HOUR, t.createdAt, t.completedAt)) FROM ManualReviewTask t WHERE t.status = 'RESOLVED' AND t.completedAt BETWEEN :startDate AND :endDate")
    Double calculateAverageResolutionTime(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Calculate SLA compliance rate
     */
    @Query("SELECT CAST(COUNT(CASE WHEN t.slaBreached = false THEN 1 END) AS DOUBLE) / COUNT(*) * 100 FROM ManualReviewTask t WHERE t.status = 'RESOLVED' AND t.completedAt BETWEEN :startDate AND :endDate")
    Double calculateSLAComplianceRate(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    // ========================================================================
    // TEAM WORKLOAD QUERIES
    // ========================================================================

    /**
     * Find teams with highest workload
     */
    @Query("SELECT t.assignedTo, COUNT(t) as taskCount FROM ManualReviewTask t WHERE t.status IN ('ASSIGNED', 'IN_PROGRESS') GROUP BY t.assignedTo ORDER BY taskCount DESC")
    List<Object[]> findTeamWorkload();

    /**
     * Check if entity already has open review task
     */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM ManualReviewTask t WHERE t.entityType = :entityType AND t.entityId = :entityId AND t.status IN ('PENDING', 'ASSIGNED', 'IN_PROGRESS')")
    boolean hasOpenReviewTask(@Param("entityType") String entityType, @Param("entityId") String entityId);
}
