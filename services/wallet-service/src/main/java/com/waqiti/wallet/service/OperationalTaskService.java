package com.waqiti.wallet.service;

import com.waqiti.wallet.domain.OperationalTask;
import com.waqiti.wallet.repository.OperationalTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Operational Task Service
 *
 * Manages operational tasks requiring manual intervention by operations team.
 * Tasks are typically created when automated processes fail and require
 * human review, decision-making, or manual execution.
 *
 * TASK TYPES:
 * - EMERGENCY_MANUAL_FREEZE: Critical account freeze requiring manual execution
 * - MANUAL_TRANSACTION_REVIEW: Transaction requiring manual review
 * - SYSTEM_RECONCILIATION: Reconciliation discrepancy requiring manual fix
 * - COMPLIANCE_REVIEW: Manual compliance review required
 * - CUSTOMER_SUPPORT_ESCALATION: Customer issue requiring ops team attention
 *
 * SLA MANAGEMENT:
 * - CRITICAL priority: 15 minutes SLA
 * - HIGH priority: 1 hour SLA
 * - MEDIUM priority: 4 hours SLA
 * - LOW priority: 24 hours SLA
 *
 * @author Waqiti Operations Team
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OperationalTaskService {

    private final OperationalTaskRepository operationalTaskRepository;

    /**
     * Create urgent task requiring immediate attention
     *
     * @param task Operational task to create
     * @return Created task with generated ID
     */
    public OperationalTask createUrgentTask(OperationalTask task) {
        try {
            // Set creation timestamp
            task.setCreatedAt(LocalDateTime.now());

            // Set default status if not provided
            if (task.getStatus() == null) {
                task.setStatus("PENDING");
            }

            // Calculate due time based on SLA
            if (task.getDueAt() == null && task.getSlaMinutes() != null) {
                task.setDueAt(LocalDateTime.now().plusMinutes(task.getSlaMinutes()));
            }

            // Save task
            OperationalTask savedTask = operationalTaskRepository.save(task);

            log.error("OPERATIONAL_TASK: Created urgent operational task - ID: {}, Type: {}, Priority: {}, " +
                            "User: {}, SLA: {} min, DueAt: {}",
                    savedTask.getId(), savedTask.getType(), savedTask.getPriority(),
                    savedTask.getUserId(), savedTask.getSlaMinutes(), savedTask.getDueAt());

            return savedTask;

        } catch (Exception e) {
            log.error("CRITICAL: Failed to create operational task - Type: {}, User: {}",
                    task.getType(), task.getUserId(), e);
            throw new RuntimeException("Failed to create operational task", e);
        }
    }

    /**
     * Create standard task
     */
    public OperationalTask createTask(OperationalTask task) {
        return createUrgentTask(task); // Uses same logic
    }

    /**
     * Get task by ID
     */
    @Transactional(readOnly = true)
    public OperationalTask getTask(String taskId) {
        return operationalTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Operational task not found: " + taskId));
    }

    /**
     * Get all tasks for a user
     */
    @Transactional(readOnly = true)
    public List<OperationalTask> getTasksByUser(UUID userId) {
        return operationalTaskRepository.findByUserId(userId);
    }

    /**
     * Get pending tasks
     */
    @Transactional(readOnly = true)
    public List<OperationalTask> getPendingTasks() {
        return operationalTaskRepository.findByStatus("PENDING");
    }

    /**
     * Get overdue tasks
     */
    @Transactional(readOnly = true)
    public List<OperationalTask> getOverdueTasks() {
        return operationalTaskRepository.findOverdueTasks(LocalDateTime.now());
    }

    /**
     * Get tasks by priority
     */
    @Transactional(readOnly = true)
    public List<OperationalTask> getTasksByPriority(OperationalTask.Priority priority) {
        return operationalTaskRepository.findByPriority(priority);
    }

    /**
     * Assign task to operator
     */
    public OperationalTask assignTask(String taskId, String assignedTo) {
        try {
            OperationalTask task = getTask(taskId);

            task.setAssignedTo(assignedTo);
            task.setAssignedAt(LocalDateTime.now());
            task.setStatus("ASSIGNED");
            task.setUpdatedAt(LocalDateTime.now());

            OperationalTask updated = operationalTaskRepository.save(task);

            log.info("OPERATIONAL_TASK: Task assigned - ID: {}, AssignedTo: {}", taskId, assignedTo);

            return updated;

        } catch (Exception e) {
            log.error("Failed to assign operational task - ID: {}, AssignedTo: {}", taskId, assignedTo, e);
            throw new RuntimeException("Failed to assign task", e);
        }
    }

    /**
     * Start work on task
     */
    public OperationalTask startTask(String taskId) {
        try {
            OperationalTask task = getTask(taskId);

            task.setStatus("IN_PROGRESS");
            task.setStartedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());

            OperationalTask updated = operationalTaskRepository.save(task);

            log.info("OPERATIONAL_TASK: Task started - ID: {}, AssignedTo: {}", taskId, task.getAssignedTo());

            return updated;

        } catch (Exception e) {
            log.error("Failed to start operational task - ID: {}", taskId, e);
            throw new RuntimeException("Failed to start task", e);
        }
    }

    /**
     * Complete task
     */
    public OperationalTask completeTask(String taskId, String outcome, String completedBy) {
        try {
            OperationalTask task = getTask(taskId);

            task.setStatus("COMPLETED");
            task.setCompletedAt(LocalDateTime.now());
            task.setOutcome(outcome);
            task.setUpdatedAt(LocalDateTime.now());

            if (task.getAssignedTo() == null) {
                task.setAssignedTo(completedBy);
            }

            OperationalTask updated = operationalTaskRepository.save(task);

            log.info("OPERATIONAL_TASK: Task completed - ID: {}, Outcome: {}, CompletedBy: {}",
                    taskId, outcome, completedBy);

            return updated;

        } catch (Exception e) {
            log.error("Failed to complete operational task - ID: {}", taskId, e);
            throw new RuntimeException("Failed to complete task", e);
        }
    }

    /**
     * Escalate task due to timeout or complexity
     */
    public OperationalTask escalateTask(String taskId, String escalationReason) {
        try {
            OperationalTask task = getTask(taskId);

            task.setStatus("ESCALATED");
            task.setEscalatedAt(LocalDateTime.now());
            task.setEscalationReason(escalationReason);
            task.setUpdatedAt(LocalDateTime.now());

            // Upgrade priority if not already critical
            if (task.getPriority() != OperationalTask.Priority.CRITICAL) {
                OperationalTask.Priority oldPriority = task.getPriority();
                task.setPriority(OperationalTask.Priority.CRITICAL);
                log.warn("OPERATIONAL_TASK: Task escalated with priority upgrade - ID: {}, OldPriority: {}, NewPriority: CRITICAL",
                        taskId, oldPriority);
            }

            // Reduce SLA for escalated tasks
            task.setSlaMinutes(15); // 15 minutes for escalated tasks
            task.setDueAt(LocalDateTime.now().plusMinutes(15));

            OperationalTask updated = operationalTaskRepository.save(task);

            log.error("OPERATIONAL_TASK: Task escalated - ID: {}, Reason: {}", taskId, escalationReason);

            return updated;

        } catch (Exception e) {
            log.error("Failed to escalate operational task - ID: {}", taskId, e);
            throw new RuntimeException("Failed to escalate task", e);
        }
    }

    /**
     * Cancel task
     */
    public OperationalTask cancelTask(String taskId, String cancellationReason) {
        try {
            OperationalTask task = getTask(taskId);

            task.setStatus("CANCELLED");
            task.setCancellationReason(cancellationReason);
            task.setUpdatedAt(LocalDateTime.now());

            OperationalTask updated = operationalTaskRepository.save(task);

            log.info("OPERATIONAL_TASK: Task cancelled - ID: {}, Reason: {}", taskId, cancellationReason);

            return updated;

        } catch (Exception e) {
            log.error("Failed to cancel operational task - ID: {}", taskId, e);
            throw new RuntimeException("Failed to cancel task", e);
        }
    }

    /**
     * Get count of pending tasks
     */
    @Transactional(readOnly = true)
    public long getPendingTaskCount() {
        return operationalTaskRepository.countByStatus("PENDING");
    }

    /**
     * Get count of overdue tasks
     */
    @Transactional(readOnly = true)
    public long getOverdueTaskCount() {
        return operationalTaskRepository.countOverdueTasks(LocalDateTime.now());
    }

    /**
     * Get count of critical tasks
     */
    @Transactional(readOnly = true)
    public long getCriticalTaskCount() {
        return operationalTaskRepository.countByPriority(OperationalTask.Priority.CRITICAL);
    }
}
