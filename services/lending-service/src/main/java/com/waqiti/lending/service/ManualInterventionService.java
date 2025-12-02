package com.waqiti.lending.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manual Intervention Service
 *
 * Handles creation and management of manual intervention tasks when automated
 * processes fail or require human review.
 *
 * This service is critical for:
 * - Financial transaction failures that require immediate attention
 * - Loan processing errors that could impact borrowers
 * - Compliance issues requiring review
 * - System errors affecting critical operations
 *
 * All manual intervention tasks are prioritized and tracked for resolution
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ManualInterventionService {

    /**
     * Create critical manual intervention task
     *
     * Creates a high-priority task requiring immediate human intervention
     * Typically used for:
     * - Failed loan disbursements
     * - Payment processing failures
     * - Critical system errors
     * - Compliance violations
     *
     * @param taskType Type of intervention task
     * @param description Detailed description of the issue
     * @param priority Priority level (CRITICAL, HIGH, MEDIUM, LOW)
     * @param eventData Original event data that caused the issue
     * @param exception Exception that occurred
     * @return Task ID for tracking
     */
    public String createCriticalTask(
            String taskType,
            String description,
            String priority,
            Object eventData,
            Exception exception) {

        String taskId = "TASK-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();

        log.error("Creating CRITICAL manual intervention task - TaskID: {}, Type: {}, Priority: {}",
                taskId, taskType, priority);

        Map<String, Object> taskData = new HashMap<>();
        taskData.put("taskId", taskId);
        taskData.put("taskType", taskType);
        taskData.put("description", description);
        taskData.put("priority", priority);
        taskData.put("eventData", eventData);
        taskData.put("errorMessage", exception != null ? exception.getMessage() : "No exception");
        taskData.put("stackTrace", exception != null ? getStackTraceAsString(exception) : "");
        taskData.put("status", "OPEN");
        taskData.put("createdAt", Instant.now());
        taskData.put("assignedTo", null); // Will be assigned by ops team
        taskData.put("resolvedAt", null);
        taskData.put("resolutionNotes", null);

        // TODO: Store in manual_intervention_tasks table
        // TODO: Send urgent notification to operations team
        // TODO: Create incident in incident management system
        // TODO: Escalate if not resolved within SLA

        // For CRITICAL tasks, send immediate notifications
        if ("CRITICAL".equals(priority)) {
            sendCriticalTaskNotification(taskId, taskType, description);
        }

        log.error("Manual intervention task created - TaskID: {}, Type: {}, Description: {}",
                taskId, taskType, description);

        return taskId;
    }

    /**
     * Create standard manual intervention task
     *
     * Creates a standard priority task for manual review
     *
     * @param taskType Type of intervention task
     * @param description Description of the issue
     * @param relatedEntityId ID of related entity (loan, application, etc.)
     * @return Task ID
     */
    public String createTask(String taskType, String description, String relatedEntityId) {
        return createCriticalTask(taskType, description, "MEDIUM", relatedEntityId, null);
    }

    /**
     * Create loan processing failure task
     *
     * Specific task for loan processing failures
     *
     * @param loanId Loan identifier
     * @param userId User identifier
     * @param amount Loan amount
     * @param failureReason Reason for failure
     * @param exception Exception that occurred
     * @return Task ID
     */
    public String createLoanProcessingFailureTask(
            String loanId,
            UUID userId,
            java.math.BigDecimal amount,
            String failureReason,
            Exception exception) {

        String description = String.format(
                "CRITICAL LOAN PROCESSING FAILURE\n" +
                "Loan ID: %s\n" +
                "User ID: %s\n" +
                "Amount: $%,.2f\n" +
                "Failure Reason: %s\n" +
                "Exception: %s\n\n" +
                "IMMEDIATE ACTION REQUIRED:\n" +
                "1. Review loan details and transaction logs\n" +
                "2. Verify if funds were disbursed\n" +
                "3. Contact borrower if necessary\n" +
                "4. Complete manual processing if needed\n" +
                "5. Update loan status appropriately",
                loanId, userId, amount, failureReason,
                exception != null ? exception.getMessage() : "Unknown"
        );

        return createCriticalTask(
                "LOAN_PROCESSING_FAILURE",
                description,
                "CRITICAL",
                Map.of("loanId", loanId, "userId", userId, "amount", amount),
                exception
        );
    }

    /**
     * Create payment processing failure task
     *
     * Task for failed payment processing
     *
     * @param loanId Loan identifier
     * @param paymentId Payment identifier
     * @param amount Payment amount
     * @param failureReason Reason for failure
     * @return Task ID
     */
    public String createPaymentFailureTask(
            String loanId,
            String paymentId,
            java.math.BigDecimal amount,
            String failureReason) {

        String description = String.format(
                "PAYMENT PROCESSING FAILURE\n" +
                "Loan ID: %s\n" +
                "Payment ID: %s\n" +
                "Amount: $%,.2f\n" +
                "Failure Reason: %s\n\n" +
                "ACTION REQUIRED:\n" +
                "1. Verify payment was not duplicated\n" +
                "2. Check if borrower's account was debited\n" +
                "3. Retry payment or process manually\n" +
                "4. Update payment records\n" +
                "5. Notify borrower if needed",
                loanId, paymentId, amount, failureReason
        );

        return createCriticalTask(
                "PAYMENT_PROCESSING_FAILURE",
                description,
                "HIGH",
                Map.of("loanId", loanId, "paymentId", paymentId, "amount", amount),
                null
        );
    }

    /**
     * Create disbursement failure task
     *
     * Task for failed fund disbursements
     *
     * @param loanId Loan identifier
     * @param userId User identifier
     * @param amount Disbursement amount
     * @param method Disbursement method
     * @param failureReason Failure reason
     * @return Task ID
     */
    public String createDisbursementFailureTask(
            String loanId,
            UUID userId,
            java.math.BigDecimal amount,
            String method,
            String failureReason) {

        String description = String.format(
                "CRITICAL DISBURSEMENT FAILURE\n" +
                "Loan ID: %s\n" +
                "User ID: %s\n" +
                "Amount: $%,.2f\n" +
                "Method: %s\n" +
                "Failure Reason: %s\n\n" +
                "IMMEDIATE ACTION REQUIRED:\n" +
                "1. Verify loan approval is valid\n" +
                "2. Check borrower's bank account details\n" +
                "3. Retry disbursement or use alternative method\n" +
                "4. Contact borrower about delay\n" +
                "5. Update loan status and disbursement records",
                loanId, userId, amount, method, failureReason
        );

        return createCriticalTask(
                "DISBURSEMENT_FAILURE",
                description,
                "CRITICAL",
                Map.of("loanId", loanId, "userId", userId, "amount", amount, "method", method),
                null
        );
    }

    /**
     * Create compliance violation task
     *
     * Task for potential compliance violations
     *
     * @param violationType Type of violation
     * @param description Violation description
     * @param relatedEntityId Related entity ID
     * @return Task ID
     */
    public String createComplianceViolationTask(
            String violationType,
            String description,
            String relatedEntityId) {

        String fullDescription = String.format(
                "COMPLIANCE VIOLATION DETECTED\n" +
                "Violation Type: %s\n" +
                "Entity ID: %s\n" +
                "Description: %s\n\n" +
                "ACTION REQUIRED:\n" +
                "1. Review compliance requirements\n" +
                "2. Investigate violation details\n" +
                "3. Take corrective action\n" +
                "4. Document resolution\n" +
                "5. Report to compliance officer if necessary",
                violationType, relatedEntityId, description
        );

        return createCriticalTask(
                "COMPLIANCE_VIOLATION",
                fullDescription,
                "CRITICAL",
                Map.of("violationType", violationType, "entityId", relatedEntityId),
                null
        );
    }

    /**
     * Send critical task notification
     *
     * Sends urgent notifications for critical tasks
     *
     * @param taskId Task identifier
     * @param taskType Task type
     * @param description Task description
     */
    private void sendCriticalTaskNotification(String taskId, String taskType, String description) {
        log.error("CRITICAL TASK NOTIFICATION - TaskID: {}, Type: {}", taskId, taskType);

        // TODO: Send PagerDuty/OpsGenie alert
        // TODO: Send email to operations team
        // TODO: Send Slack notification to #critical-alerts channel
        // TODO: Create incident in incident management system
        // TODO: Start SLA timer for resolution

        log.error("Critical task notifications sent for TaskID: {}", taskId);
    }

    /**
     * Convert exception stack trace to string
     */
    private String getStackTraceAsString(Exception exception) {
        if (exception == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(exception.getClass().getName()).append(": ").append(exception.getMessage()).append("\n");

        for (StackTraceElement element : exception.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }

        if (exception.getCause() != null) {
            sb.append("Caused by: ").append(getStackTraceAsString((Exception) exception.getCause()));
        }

        return sb.toString();
    }

    /**
     * Resolve manual intervention task
     *
     * Marks a task as resolved
     *
     * @param taskId Task identifier
     * @param resolvedBy Who resolved the task
     * @param resolutionNotes Notes about the resolution
     */
    public void resolveTask(String taskId, String resolvedBy, String resolutionNotes) {
        log.info("Resolving manual intervention task - TaskID: {}, ResolvedBy: {}", taskId, resolvedBy);

        // TODO: Update task status to RESOLVED
        // TODO: Record resolution timestamp
        // TODO: Store resolution notes
        // TODO: Calculate resolution time vs SLA
        // TODO: Send notification to task creator

        log.info("Task resolved - TaskID: {}", taskId);
    }

    /**
     * Escalate task
     *
     * Escalates a task that hasn't been resolved within SLA
     *
     * @param taskId Task identifier
     * @param escalationReason Reason for escalation
     */
    public void escalateTask(String taskId, String escalationReason) {
        log.warn("Escalating manual intervention task - TaskID: {}, Reason: {}", taskId, escalationReason);

        // TODO: Update task priority
        // TODO: Reassign to senior operations
        // TODO: Send escalation notifications
        // TODO: Create incident if not exists

        log.warn("Task escalated - TaskID: {}", taskId);
    }
}
