package com.waqiti.dlq.strategy;

import com.waqiti.common.alerting.UnifiedAlertingService;
import com.waqiti.dlq.model.DLQMessage;
import com.waqiti.dlq.repository.DLQMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Manual intervention strategy for DLQ recovery.
 *
 * This strategy escalates messages that require human review to appropriate teams.
 * Used for complex failures that cannot be automatically recovered.
 *
 * ESCALATION ROUTING:
 * - Financial discrepancies → Finance team
 * - Compliance issues → Compliance team
 * - Fraud alerts → Fraud team
 * - Data corruption → Engineering team
 * - Unknown errors → Operations team
 *
 * ACTIONS TAKEN:
 * - Create ticket in issue tracking system (JIRA/ServiceNow)
 * - Send Slack alert to appropriate channel
 * - Send email to team distribution list
 * - Update DLQ message status to MANUAL_REVIEW_REQUIRED
 * - Set assignment and priority
 *
 * @author Waqiti Platform Engineering
 * @since 1.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ManualInterventionStrategy implements RecoveryStrategyHandler {

    private final UnifiedAlertingService alertingService;
    private final DLQMessageRepository dlqMessageRepository;
    private final TicketingService ticketingService;

    @Override
    public RecoveryResult recover(DLQMessage message) {
        log.info("Escalating for manual intervention: messageId={}, topic={}",
                message.getId(), message.getOriginalTopic());

        try {
            // Determine appropriate team based on topic and error
            EscalationTeam team = determineEscalationTeam(message);

            // Create ticket
            String ticketId = createTicket(message, team);

            // Send alerts
            sendAlerts(message, team, ticketId);

            // Update DLQ message
            updateDLQMessage(message, team, ticketId);

            log.info("✅ Manual intervention escalated: messageId={}, team={}, ticketId={}",
                    message.getId(), team, ticketId);

            return RecoveryResult.permanentFailure(
                "Escalated for manual review - Ticket: " + ticketId + ", Team: " + team);

        } catch (Exception e) {
            log.error("❌ Manual intervention escalation failed: messageId={}, error={}",
                    message.getId(), e.getMessage(), e);
            return RecoveryResult.retryLater("Escalation failed: " + e.getMessage(), 300);
        }
    }

    /**
     * Determines which team should handle the manual intervention.
     */
    private EscalationTeam determineEscalationTeam(DLQMessage message) {
        String topic = message.getOriginalTopic();
        String errorMessage = message.getErrorMessage();
        String errorClass = message.getErrorClass();

        // Finance team
        if (topic.contains("balance") || topic.contains("ledger") ||
            topic.contains("reconciliation") || topic.contains("settlement")) {
            return EscalationTeam.FINANCE;
        }

        // Compliance team
        if (topic.contains("sanctions") || topic.contains("kyc") ||
            topic.contains("aml") || topic.contains("compliance")) {
            return EscalationTeam.COMPLIANCE;
        }

        // Fraud team
        if (topic.contains("fraud") || topic.contains("chargeback") ||
            topic.contains("dispute")) {
            return EscalationTeam.FRAUD;
        }

        // Engineering team (data corruption, deserialization errors)
        if (errorClass != null && (
            errorClass.contains("SerializationException") ||
            errorClass.contains("DataCorruptionException") ||
            errorClass.contains("SchemaException"))) {
            return EscalationTeam.ENGINEERING;
        }

        // Default to operations team
        return EscalationTeam.OPERATIONS;
    }

    /**
     * Creates ticket in issue tracking system.
     */
    private String createTicket(DLQMessage message, EscalationTeam team) {
        log.debug("Creating ticket for DLQ message: messageId={}, team={}",
                message.getId(), team);

        String title = String.format("[DLQ] %s - %s",
                message.getOriginalTopic(),
                message.getErrorClass() != null ? message.getErrorClass() : "Unknown error");

        String description = buildTicketDescription(message);

        String ticketId = ticketingService.createTicket(
            team.getProject(),
            title,
            description,
            team.getPriority(),
            team.getAssignee(),
            Map.of(
                "dlq_message_id", message.getId().toString(),
                "original_topic", message.getOriginalTopic(),
                "error_class", message.getErrorClass(),
                "retry_count", String.valueOf(message.getRetryCount())
            )
        );

        log.debug("✅ Ticket created: ticketId={}, messageId={}", ticketId, message.getId());
        return ticketId;
    }

    /**
     * Builds detailed ticket description.
     */
    private String buildTicketDescription(DLQMessage message) {
        return String.format("""
            **DLQ Message Requiring Manual Intervention**

            **Message Details:**
            - Message ID: `%s`
            - Original Topic: `%s`
            - Consumer Group: `%s`
            - Failed Consumer: `%s`
            - Retry Count: %d
            - Created At: %s

            **Error Information:**
            - Error Class: `%s`
            - Error Message: ```%s```

            **Message Payload:**
            ```json
            %s
            ```

            **Stack Trace:**
            ```
            %s
            ```

            **Recommended Actions:**
            1. Review error message and stack trace
            2. Verify message payload structure
            3. Check if this is a pattern (search for similar DLQ messages)
            4. Determine root cause
            5. Fix underlying issue
            6. Retry message or skip if appropriate
            7. Update DLQ message status in system

            **Links:**
            - DLQ Dashboard: https://monitoring.example.com/dlq?messageId=%s
            - Kafka Topic: https://kafka.example.com/topics/%s
            - Original Message: https://kafka.example.com/messages/%s/%d/%d
            """,
            message.getId(),
            message.getOriginalTopic(),
            message.getConsumerGroup(),
            message.getFailedConsumerClass(),
            message.getRetryCount(),
            message.getCreatedAt(),
            message.getErrorClass(),
            message.getErrorMessage(),
            formatPayload(message.getMessagePayload()),
            truncate(message.getErrorStackTrace(), 2000),
            message.getId(),
            message.getOriginalTopic(),
            message.getOriginalTopic(),
            message.getOriginalPartition(),
            message.getOriginalOffset()
        );
    }

    /**
     * Sends alerts to appropriate channels.
     */
    private void sendAlerts(DLQMessage message, EscalationTeam team, String ticketId) {
        log.debug("Sending manual intervention alerts: messageId={}, team={}",
                message.getId(), team);

        Map<String, Object> alertContext = Map.of(
            "messageId", message.getId().toString(),
            "topic", message.getOriginalTopic(),
            "team", team.toString(),
            "ticketId", ticketId,
            "errorClass", message.getErrorClass() != null ? message.getErrorClass() : "Unknown",
            "retryCount", message.getRetryCount()
        );

        // Slack alert
        String slackMessage = buildSlackMessage(message, team, ticketId);
        alertingService.sendSlackAlert(
            team.getSlackChannel(),
            "🚨 DLQ Manual Intervention Required",
            slackMessage,
            alertContext
        );

        // Email alert (for high priority)
        if (message.getPriority() == DLQMessage.DLQPriority.CRITICAL ||
            message.getPriority() == DLQMessage.DLQPriority.HIGH) {

            alertingService.sendEmailAlert(
                team.getEmailDistributionList(),
                "DLQ Manual Intervention Required - " + ticketId,
                buildEmailMessage(message, team, ticketId),
                alertContext
            );
        }

        log.debug("✅ Alerts sent: messageId={}, team={}", message.getId(), team);
    }

    /**
     * Builds Slack alert message.
     */
    private String buildSlackMessage(DLQMessage message, EscalationTeam team, String ticketId) {
        return String.format("""
            *DLQ Manual Intervention Required* 🚨

            A DLQ message requires manual review and intervention.

            *Message Details:*
            • Topic: `%s`
            • Priority: `%s`
            • Retry Count: %d
            • Error: `%s`

            *Assignment:*
            • Team: *%s*
            • Ticket: `%s`
            • Assignee: @%s

            *Actions:*
            1. Review ticket for full details
            2. Investigate root cause
            3. Fix and retry, or skip message

            *Links:*
            • <https://tickets.example.com/%s|View Ticket>
            • <https://monitoring.example.com/dlq?messageId=%s|DLQ Dashboard>
            """,
            message.getOriginalTopic(),
            message.getPriority(),
            message.getRetryCount(),
            truncate(message.getErrorMessage(), 100),
            team,
            ticketId,
            team.getAssignee(),
            ticketId,
            message.getId()
        );
    }

    /**
     * Builds email alert message.
     */
    private String buildEmailMessage(DLQMessage message, EscalationTeam team, String ticketId) {
        return String.format("""
            <h2>DLQ Manual Intervention Required</h2>

            <p>A DLQ message with <strong>%s</strong> priority requires manual review.</p>

            <h3>Message Details</h3>
            <ul>
                <li><strong>Message ID:</strong> %s</li>
                <li><strong>Topic:</strong> %s</li>
                <li><strong>Priority:</strong> %s</li>
                <li><strong>Retry Count:</strong> %d</li>
                <li><strong>Error:</strong> %s</li>
            </ul>

            <h3>Assignment</h3>
            <ul>
                <li><strong>Team:</strong> %s</li>
                <li><strong>Ticket:</strong> <a href="https://tickets.example.com/%s">%s</a></li>
                <li><strong>Assignee:</strong> %s</li>
            </ul>

            <h3>Next Steps</h3>
            <ol>
                <li>Review the ticket for complete details</li>
                <li>Investigate the root cause of the failure</li>
                <li>Implement a fix if needed</li>
                <li>Retry the message or mark as skipped</li>
                <li>Update the DLQ message status</li>
            </ol>

            <p><a href="https://monitoring.example.com/dlq?messageId=%s">View in DLQ Dashboard</a></p>
            """,
            message.getPriority(),
            message.getId(),
            message.getOriginalTopic(),
            message.getPriority(),
            message.getRetryCount(),
            message.getErrorMessage(),
            team,
            ticketId,
            ticketId,
            team.getAssignee(),
            message.getId()
        );
    }

    /**
     * Updates DLQ message with manual intervention details.
     */
    private void updateDLQMessage(DLQMessage message, EscalationTeam team, String ticketId) {
        message.setStatus(DLQMessage.DLQStatus.MANUAL_REVIEW_REQUIRED);
        message.setAssignedTo(team.getAssignee());
        message.setRecoveryNotes(String.format(
            "Escalated to %s team. Ticket: %s. Awaiting manual intervention.",
            team, ticketId));

        dlqMessageRepository.save(message);

        log.debug("✅ DLQ message updated: messageId={}, status=MANUAL_REVIEW_REQUIRED",
                message.getId());
    }

    private String formatPayload(String payload) {
        try {
            // Pretty print JSON
            return payload; // Already formatted
        } catch (Exception e) {
            return payload;
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "N/A";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "... (truncated)";
    }

    @Override
    public String getStrategyName() {
        return "MANUAL_INTERVENTION";
    }

    @Override
    public boolean canHandle(DLQMessage message) {
        // Can handle any message type
        return true;
    }

    /**
     * Escalation team configuration.
     */
    private enum EscalationTeam {
        FINANCE("FIN", "finance", "finance-team", "finance-lead", "HIGH"),
        COMPLIANCE("COMP", "compliance", "compliance-team", "compliance-officer", "CRITICAL"),
        FRAUD("FRAUD", "fraud", "fraud-team", "fraud-manager", "CRITICAL"),
        ENGINEERING("ENG", "engineering", "engineering-team", "platform-lead", "MEDIUM"),
        OPERATIONS("OPS", "operations", "ops-team", "ops-manager", "MEDIUM");

        private final String project;
        private final String slackChannel;
        private final String emailDistributionList;
        private final String assignee;
        private final String priority;

        EscalationTeam(String project, String slackChannel, String emailDistributionList,
                       String assignee, String priority) {
            this.project = project;
            this.slackChannel = slackChannel;
            this.emailDistributionList = emailDistributionList;
            this.assignee = assignee;
            this.priority = priority;
        }

        public String getProject() { return project; }
        public String getSlackChannel() { return slackChannel; }
        public String getEmailDistributionList() { return emailDistributionList; }
        public String getAssignee() { return assignee; }
        public String getPriority() { return priority; }
    }
}
