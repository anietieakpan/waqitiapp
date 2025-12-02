package com.waqiti.ledger.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Notification Service - Production-Ready Alert System
 *
 * Integrates PagerDuty, Slack, and Email for critical financial alerts.
 * Used by DLQ handlers, reconciliation services, and compliance monitoring.
 *
 * Security: CRITICAL - Used for production incident response
 * Compliance: SOX, Basel III - All alerts are audited
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final PagerDutyAlertService pagerDutyService;
    private final SlackNotificationService slackService;
    private final EmailAlertService emailService;

    // =========================================================================
    // EXISTING METHODS (Enhanced with actual alert delivery)
    // =========================================================================

    public void sendReconciliationCompletionNotification(Object reconciliation, String correlationId) {
        log.info("Sending reconciliation completion notification: correlationId={}", correlationId);

        // Send success notification to Slack
        slackService.sendSuccessNotification(
            "#accounting",
            String.format("✅ Reconciliation completed successfully - Correlation ID: %s", correlationId),
            null
        );
    }

    public void sendDiscrepancyAlert(Object reconciliation, Object discrepancyResult, String correlationId) {
        log.warn("Sending discrepancy alert: correlationId={}", correlationId);

        // Send warning to Slack #finance-ops
        slackService.sendWarningAlert(
            "#finance-ops",
            String.format("⚠️ Discrepancy detected - Correlation ID: %s", correlationId),
            Map.of("Status", "Review Required")
        );

        // Send email to finance team
        emailService.sendEmail(
            List.of("FINANCE_OPS"),
            "Reconciliation Discrepancy Detected",
            String.format("<p>A discrepancy was detected during reconciliation.</p><p>Correlation ID: %s</p>", correlationId)
        );
    }

    public void sendCriticalReconciliationAlert(Object reconciliation, Object discrepancyResult, String correlationId) {
        log.error("Sending critical reconciliation alert: correlationId={}", correlationId);

        // P1 PagerDuty alert
        pagerDutyService.sendP1Alert(
            String.format("CRITICAL: Reconciliation discrepancy - %s", correlationId),
            Map.of("correlationId", correlationId, "severity", "CRITICAL")
        );

        // Critical Slack alert
        slackService.sendCriticalAlert(
            "#finance-ops",
            String.format("🚨 CRITICAL reconciliation discrepancy - Correlation ID: %s", correlationId),
            Map.of("Severity", "CRITICAL", "Action Required", "Immediate")
        );

        // Critical email to CFO + Controller
        emailService.sendCriticalAlert(
            List.of("CFO", "CONTROLLER", "FINANCE_OPS"),
            String.format("CRITICAL Reconciliation Alert - %s", correlationId),
            String.format("<h2>Critical Reconciliation Alert</h2><p>A critical discrepancy requires immediate attention.</p><p>Correlation ID: %s</p>", correlationId)
        );
    }

    public void sendCriticalReconciliationFailureAlert(Object reconciliation, String correlationId) {
        log.error("Sending critical reconciliation failure alert: correlationId={}", correlationId);

        // P0 PagerDuty alert - highest priority
        pagerDutyService.sendP0Alert(
            String.format("P0: Reconciliation FAILED - %s", correlationId),
            Map.of("correlationId", correlationId, "severity", "P0", "status", "FAILED")
        );

        // Critical alert to all channels
        slackService.sendCriticalAlert(
            "#finance-ops",
            String.format("🚨 P0 ALERT: Reconciliation FAILED - Correlation ID: %s", correlationId),
            Map.of("Severity", "P0", "Status", "FAILED")
        );

        // Escalate to C-suite
        emailService.sendCriticalAlert(
            List.of("CFO", "CEO", "CTO", "CONTROLLER"),
            String.format("P0 ALERT: Reconciliation Failure - %s", correlationId),
            String.format("<h2 style='color: red;'>🚨 P0 CRITICAL ALERT</h2><p>Reconciliation has failed completely.</p><p>Correlation ID: %s</p><p><strong>Immediate action required!</strong></p>", correlationId)
        );
    }

    // =========================================================================
    // NEW PRODUCTION-READY METHODS
    // =========================================================================

    /**
     * Sends a critical alert through all channels (PagerDuty + Slack + Email)
     * Use this for catastrophic failures that require immediate C-suite attention
     */
    public void sendCriticalAlert(String title, String message, List<String> recipients) {
        log.error("🚨 CRITICAL ALERT: {} - {}", title, message);

        // P0 PagerDuty alert
        pagerDutyService.sendP0Alert(
            String.format("%s: %s", title, message),
            Map.of("title", title, "recipients", String.join(", ", recipients))
        );

        // Critical Slack alert with @channel mention
        slackService.sendCriticalAlert("#finance-ops", message, Map.of("Alert", title));

        // High-priority email
        emailService.sendCriticalAlert(recipients, title, message);
    }

    /**
     * Sends a PagerDuty alert with specified priority
     */
    public void sendPagerDutyAlert(String priority, String message) {
        pagerDutyService.sendAlert(priority, message, null);
    }

    /**
     * Sends a Slack notification to a specific channel
     */
    public void sendSlackNotification(String channel, String message) {
        slackService.sendNotification(channel, message);
    }

    /**
     * Sends an email alert to specified recipients
     */
    public void sendEmailAlert(List<String> recipients, String subject, String body) {
        emailService.sendEmail(recipients, subject, body);
    }

    /**
     * Creates a manual review task for the finance team
     *
     * This integrates with task management systems and sends appropriate
     * notifications based on priority level.
     *
     * @param taskType Type of task (e.g., "EMERGENCY_RECONCILIATION_REVIEW")
     * @param description Detailed task description
     * @param assigneeLevel Who should handle this (C_SUITE, CONTROLLER, etc.)
     * @param priority Priority level (CRITICAL, HIGH, MEDIUM, LOW)
     * @param metadata Additional task context
     */
    public void createTask(
            String taskType,
            String description,
            String assigneeLevel,
            String priority,
            Map<String, String> metadata) {

        log.info("Creating manual review task - Type: {}, Assignee: {}, Priority: {}",
                taskType, assigneeLevel, priority);

        // Route to appropriate channels based on priority
        if ("CRITICAL".equals(priority)) {
            // Critical tasks: PagerDuty P0 + Slack + Email
            pagerDutyService.sendP0Alert(
                String.format("MANUAL REVIEW REQUIRED: %s", description),
                Map.of("taskType", taskType, "assigneeLevel", assigneeLevel, "priority", priority)
            );

            slackService.sendCriticalAlert(
                "#finance-ops",
                String.format("📋 Manual Review Required: %s", description),
                metadata
            );
        } else if ("HIGH".equals(priority)) {
            // High priority: Slack + Email
            slackService.sendWarningAlert(
                "#finance-ops",
                String.format("📋 Manual Review Required: %s", description),
                metadata
            );
        } else {
            // Medium/Low: Just Slack notification
            slackService.sendNotification(
                "#finance-ops",
                String.format("📋 Manual Review: %s", description),
                metadata
            );
        }

        // Always send email for task creation
        List<String> recipients = resolveAssigneeEmails(assigneeLevel);
        emailService.sendEmail(
            recipients,
            String.format("Manual Review Task: %s", taskType),
            buildTaskEmailBody(taskType, description, assigneeLevel, priority, metadata)
        );
    }

    /**
     * Sends emergency reconciliation notification through all appropriate channels
     */
    public void sendEmergencyReconciliationNotification(
            String accountId,
            String discrepancy,
            String severity,
            Map<String, String> details) {

        log.error("🚨 Emergency reconciliation notification - Account: {}, Discrepancy: {}, Severity: {}",
                accountId, discrepancy, severity);

        // Determine recipients based on severity
        List<String> recipients = switch (severity.toUpperCase()) {
            case "CRITICAL" -> List.of("CFO", "CEO", "CTO", "CONTROLLER", "FINANCE_OPS", "COMPLIANCE");
            case "HIGH" -> List.of("CFO", "CONTROLLER", "FINANCE_OPS");
            case "MEDIUM" -> List.of("CONTROLLER", "FINANCE_MANAGER", "FINANCE_OPS");
            default -> List.of("FINANCE_OPS");
        };

        // PagerDuty alert for CRITICAL and HIGH
        if ("CRITICAL".equals(severity)) {
            pagerDutyService.sendP0Alert(
                String.format("Emergency Reconciliation - Account: %s, Discrepancy: %s", accountId, discrepancy),
                Map.of("accountId", accountId, "discrepancy", discrepancy, "severity", severity)
            );
        } else if ("HIGH".equals(severity)) {
            pagerDutyService.sendP1Alert(
                String.format("Emergency Reconciliation - Account: %s, Discrepancy: %s", accountId, discrepancy),
                Map.of("accountId", accountId, "discrepancy", discrepancy, "severity", severity)
            );
        }

        // Slack notification
        slackService.sendReconciliationAlert("#finance-ops", accountId, discrepancy, severity);

        // Email notification with full details
        emailService.sendEmergencyReconciliationEmail(recipients, accountId, discrepancy, severity, details);
    }

    /**
     * Sends daily reconciliation summary to finance team
     */
    public void sendDailyReconciliationSummary(Map<String, Object> summary) {
        log.info("Sending daily reconciliation summary");

        // Email summary to finance ops
        emailService.sendDailyReconciliationSummary("FINANCE_OPS", summary);

        // Slack summary to accounting channel
        StringBuilder slackMessage = new StringBuilder("📊 Daily Reconciliation Summary\n");
        for (Map.Entry<String, Object> entry : summary.entrySet()) {
            slackMessage.append(String.format("• %s: %s\n", entry.getKey(), entry.getValue()));
        }
        slackService.sendNotification("#accounting", slackMessage.toString());
    }

    /**
     * Sends month-end closing notification
     */
    public void sendMonthEndClosingNotification(String period, String status, Map<String, String> details) {
        log.info("Sending month-end closing notification - Period: {}, Status: {}", period, status);

        slackService.sendNotification(
            "#accounting",
            String.format("📅 Month-End Closing - %s: %s", period, status),
            details
        );

        emailService.sendEmail(
            List.of("CFO", "CONTROLLER"),
            String.format("Month-End Closing: %s - %s", period, status),
            buildMonthEndEmailBody(period, status, details)
        );
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    /**
     * Resolves assignee level to email recipients
     */
    private List<String> resolveAssigneeEmails(String assigneeLevel) {
        return switch (assigneeLevel.toUpperCase()) {
            case "C_SUITE" -> List.of("CFO", "CEO", "CTO");
            case "CONTROLLER" -> List.of("CONTROLLER", "CFO");
            case "FINANCE_MANAGER" -> List.of("FINANCE_MANAGER", "CONTROLLER");
            case "FINANCE_OPS" -> List.of("FINANCE_OPS");
            default -> List.of("FINANCE_OPS");
        };
    }

    /**
     * Builds email body for manual review task
     */
    private String buildTaskEmailBody(
            String taskType,
            String description,
            String assigneeLevel,
            String priority,
            Map<String, String> metadata) {

        StringBuilder body = new StringBuilder();
        body.append("<h2>Manual Review Task Created</h2>\n");
        body.append(String.format("<p><strong>Task Type:</strong> %s</p>\n", taskType));
        body.append(String.format("<p><strong>Priority:</strong> <span style='color: %s;'>%s</span></p>\n",
            getPriorityColor(priority), priority));
        body.append(String.format("<p><strong>Assigned To:</strong> %s</p>\n", assigneeLevel));
        body.append(String.format("<p><strong>Description:</strong> %s</p>\n", description));

        if (metadata != null && !metadata.isEmpty()) {
            body.append("<h3>Additional Details:</h3>\n");
            body.append("<ul>\n");
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                body.append(String.format("  <li><strong>%s:</strong> %s</li>\n",
                    escapeHtml(entry.getKey()), escapeHtml(entry.getValue())));
            }
            body.append("</ul>\n");
        }

        body.append("<p><em>Please review and take appropriate action in the Waqiti Admin Dashboard.</em></p>\n");

        return body.toString();
    }

    /**
     * Builds email body for month-end closing
     */
    private String buildMonthEndEmailBody(String period, String status, Map<String, String> details) {
        StringBuilder body = new StringBuilder();
        body.append(String.format("<h2>Month-End Closing: %s</h2>\n", period));
        body.append(String.format("<p><strong>Status:</strong> %s</p>\n", status));

        if (details != null && !details.isEmpty()) {
            body.append("<h3>Details:</h3>\n");
            body.append("<ul>\n");
            for (Map.Entry<String, String> entry : details.entrySet()) {
                body.append(String.format("  <li><strong>%s:</strong> %s</li>\n",
                    escapeHtml(entry.getKey()), escapeHtml(entry.getValue())));
            }
            body.append("</ul>\n");
        }

        return body.toString();
    }

    /**
     * Gets color for priority level
     */
    private String getPriorityColor(String priority) {
        return switch (priority.toUpperCase()) {
            case "CRITICAL" -> "#dc3545"; // Red
            case "HIGH" -> "#fd7e14";     // Orange
            case "MEDIUM" -> "#ffc107";   // Yellow
            case "LOW" -> "#28a745";      // Green
            default -> "#6c757d";         // Gray
        };
    }

    /**
     * Escapes HTML to prevent XSS
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}
