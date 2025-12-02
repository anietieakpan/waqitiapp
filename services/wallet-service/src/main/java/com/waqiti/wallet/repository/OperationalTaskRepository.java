package com.waqiti.wallet.repository;

import com.waqiti.wallet.domain.OperationalTask;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Operational Task Repository
 *
 * MongoDB repository for operational task management and tracking.
 * Supports queries for task lifecycle, SLA monitoring, and workload distribution.
 *
 * @author Waqiti Operations Team
 * @since 1.0
 */
@Repository
public interface OperationalTaskRepository extends MongoRepository<OperationalTask, String> {

    /**
     * Find all tasks for a specific user
     */
    List<OperationalTask> findByUserId(UUID userId);

    /**
     * Find tasks by status
     */
    List<OperationalTask> findByStatus(String status);

    /**
     * Find tasks by priority
     */
    List<OperationalTask> findByPriority(OperationalTask.Priority priority);

    /**
     * Find tasks by assigned operator
     */
    List<OperationalTask> findByAssignedTo(String assignedTo);

    /**
     * Find overdue tasks (past due time and not completed/cancelled)
     */
    @Query("{ 'dueAt': { $lt: ?0 }, 'status': { $nin: ['COMPLETED', 'CANCELLED'] } }")
    List<OperationalTask> findOverdueTasks(LocalDateTime now);

    /**
     * Find tasks approaching due time (within next N minutes)
     */
    @Query("{ 'dueAt': { $lte: ?0, $gte: ?1 }, 'status': { $nin: ['COMPLETED', 'CANCELLED'] } }")
    List<OperationalTask> findTasksApproachingDue(LocalDateTime upperBound, LocalDateTime now);

    /**
     * Find recent tasks (created within time period)
     */
    @Query("{ 'createdAt': { $gte: ?0 } }")
    List<OperationalTask> findRecentTasks(LocalDateTime since);

    /**
     * Find tasks by type
     */
    List<OperationalTask> findByType(String type);

    /**
     * Find tasks by case ID
     */
    List<OperationalTask> findByCaseId(String caseId);

    /**
     * Count tasks by status
     */
    long countByStatus(String status);

    /**
     * Count tasks by priority
     */
    long countByPriority(OperationalTask.Priority priority);

    /**
     * Count overdue tasks
     */
    @Query(value = "{ 'dueAt': { $lt: ?0 }, 'status': { $nin: ['COMPLETED', 'CANCELLED'] } }",
           count = true)
    long countOverdueTasks(LocalDateTime now);

    /**
     * Count tasks assigned to operator
     */
    long countByAssignedTo(String assignedTo);

    /**
     * Find unassigned pending tasks (for task distribution)
     */
    @Query("{ 'status': 'PENDING', 'assignedTo': null }")
    List<OperationalTask> findUnassignedPendingTasks();
}
