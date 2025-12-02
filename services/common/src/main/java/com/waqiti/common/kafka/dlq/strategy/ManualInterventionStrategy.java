package com.waqiti.common.kafka.dlq.strategy;

import com.waqiti.common.alerting.UnifiedAlertingService;
import com.waqiti.common.kafka.dlq.DlqStatus;
import com.waqiti.common.kafka.dlq.entity.DlqRecordEntity;
import com.waqiti.common.kafka.dlq.repository.DlqRecordRepository;
import com.waqiti.common.ticketing.TicketingService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
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
 * - Security incidents → Security team
 * - Unknown errors → Operations team
 *
 * ACTIONS TAKEN:
 * - Create ticket in issue tracking system
 * - Send Slack alert to appropriate channel
 * - Send email to team distribution list
 * - Update DLQ record status to PARKED
 * - Set priority and team assignment
 *
 * PRODUCTION BEHAVIOR:
 * - Tickets created with proper priority (P0/P1/P2/P3)
 * - Team routing based on topic and error classification
 * - Comprehensive alerts with context and metadata
 * - Audit trail of all escalations
 *
 * @author Waqiti Platform Engineering
 * @version 2.0.0
 * @since 2025-11-19
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ManualInterventionStrategy implements RecoveryStrategyHandler {

    private final UnifiedAlertingService alertingService;
    private final DlqRecordRepository dlqRecordRepository;
    private final TicketingService ticketingService;
    private final MeterRegistry meterRegistry;

    @Override
    public RecoveryResult recover(DlqRecordEntity dlqRecord) {
        log.info("🚨 Escalating for manual intervention: messageId={}, topic={}",
                dlqRecord.getMessageId(), dlqRecord.getTopic());

        try {
            // Determine appropriate team based on topic and error
            EscalationTeam team = determineEscalationTeam(dlqRecord);

            // Determine priority
            String priority = determinePriority(dlqRecord);

            // Create ticket
            String ticketId = createTicket(dlqRecord, team, priority);

            // Send alerts
            sendAlerts(dlqRecord, team, ticketId);

            // Update DLQ record
            updateDLQRecord(dlqRecord, team, ticketId);

            // Record metrics
            recordMetric(team, priority);

            log.info("✅ Manual intervention escalated: messageId={}, team={}, ticketId={}",
                    dlqRecord.getMessageId(), team, ticketId);

            return RecoveryResult.permanentFailure(
                String.format("Escalated for manual review - Ticket: %s, Team: %s", ticketId, team));

        } catch (Exception e) {
            log.error("❌ Manual intervention escalation failed: messageId={}, error={}",
                    dlqRecord.getMessageId(), e.getMessage(), e);
            return RecoveryResult.retryLater("Escalation failed: " + e.getMessage(), 300);
        }
    }

    /**
     * Determines which team should handle the manual intervention.
     */
    private EscalationTeam determineEscalationTeam(DlqRecordEntity dlqRecord) {
        String topic = dlqRecord.getTopic();
        String failureReason = dlqRecord.getLastFailureReason();

        // Finance team - financial discrepancies
        if (topic.contains("balance") || topic.contains("ledger") ||
            topic.contains("reconciliation") || topic.contains("settlement") ||
            topic.contains("accounting")) {
            return EscalationTeam.FINANCE;
        }

        // Compliance team - regulatory issues
        if (topic.contains("sanctions") || topic.contains("kyc") ||
            topic.contains("aml") || topic.contains("compliance") ||
            topic.contains("regulatory") || topic.contains("fincen")) {
            return EscalationTeam.COMPLIANCE;
        }

        // Fraud team - fraud and chargebacks
        if (topic.contains("fraud") || topic.contains("chargeback") ||
            topic.contains("dispute") || topic.contains("risk")) {
            return EscalationTeam.FRAUD;
        }

        // Security team - security incidents
        if (topic.contains("security") || topic.contains("breach") ||
            topic.contains("unauthorized") || topic.contains("suspicious") ||
            (failureReason != null && failureReason.contains("SecurityException"))) {
            return EscalationTeam.SECURITY;
        }

        // Engineering team - technical/data issues
        if (failureReason != null && (
            failureReason.contains("NullPointerException") ||
            failureReason.contains("SQLException") ||
            failureReason.contains("ConcurrentModificationException") ||
            failureReason.contains("OutOfMemoryError"))) {
            return EscalationTeam.ENGINEERING;
        }

        // Operations team - default for unknown issues
        return EscalationTeam.OPERATIONS;
    }

    /**
     * Determines priority based on topic criticality and retry count.
     */
    private String determinePriority(DlqRecordEntity dlqRecord) {
        String topic = dlqRecord.getTopic();
        int retryCount = dlqRecord.getRetryCount();

        // P0 - Critical (financial transactions, compliance)
        if (topic.contains("payment") || topic.contains("transaction") ||
            topic.contains("ledger") || topic.contains("compliance") ||
            topic.contains("fraud") || topic.contains("settlement")) {
            return "P0";
        }

        // P1 - High (after multiple retries or high-value topics)
        if (retryCount >= 5 || topic.contains("kyc") ||
            topic.contains("wallet") || topic.contains("account")) {
            return "P1";
        }

        // P2 - Medium (operational issues)
        if (retryCount >= 3 || topic.contains("notification") ||
            topic.contains("analytics")) {
            return "P2";
        }

        // P3 - Low (informational, non-critical)
        return "P3";
    }

    /**
     * Creates a support ticket.
     */
    private String createTicket(DlqRecordEntity dlqRecord, EscalationTeam team, String priority) {
        String title = String.format("[DLQ] %s - %s",
                priority, dlqRecord.getTopic());

        String description = buildTicketDescription(dlqRecord);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("messageId", dlqRecord.getMessageId());
        metadata.put("topic", dlqRecord.getTopic());
        metadata.put("partition", dlqRecord.getPartition());
        metadata.put("offset", dlqRecord.getOffset());
        metadata.put("retryCount", dlqRecord.getRetryCount());
        metadata.put("failureReason", dlqRecord.getLastFailureReason());
        metadata.put("serviceName", dlqRecord.getServiceName());
        metadata.put("createdAt", dlqRecord.getCreatedAt());

        return ticketingService.createTicket(title, description, team.name(), priority, metadata);
    }

    /**
     * Builds ticket description with context.
     */
    private String buildTicketDescription(DlqRecordEntity dlqRecord) {
        return String.format("""
            **DLQ Manual Intervention Required**

            **Message Details:**
            - Message ID: %s
            - Topic: %s
            - Service: %s
            - Retry Count: %d

            **Failure Information:**
            - Last Failure: %s
            - First Failure: %s
            - Failure Reason: %s

            **Message Payload:**
            ```
            %s
            ```

            **Error Stack Trace:**
            ```
            %s
            ```

            **Action Required:**
            Please investigate and resolve this DLQ message manually.
            Once resolved, update the ticket and mark the DLQ record as resolved.
            """,
                dlqRecord.getMessageId(),
                dlqRecord.getTopic(),
                dlqRecord.getServiceName(),
                dlqRecord.getRetryCount(),
                dlqRecord.getLastFailureTime(),
                dlqRecord.getFirstFailureTime(),
                dlqRecord.getLastFailureReason(),
                truncate(dlqRecord.getMessageValue(), 500),
                truncate(dlqRecord.getErrorStackTrace(), 1000)
        );
    }

    /**
     * Sends alerts to appropriate channels.
     */
    private void sendAlerts(DlqRecordEntity dlqRecord, EscalationTeam team, String ticketId) {
        String alertTitle = String.format("DLQ Manual Intervention Required - %s", ticketId);

        Map<String, Object> alertMetadata = new HashMap<>();
        alertMetadata.put("ticketId", ticketId);
        alertMetadata.put("team", team.name());
        alertMetadata.put("messageId", dlqRecord.getMessageId());
        alertMetadata.put("topic", dlqRecord.getTopic());
        alertMetadata.put("retryCount", dlqRecord.getRetryCount());
        alertMetadata.put("serviceName", dlqRecord.getServiceName());

        // Send alert via UnifiedAlertingService
        alertingService.sendCriticalAlert(alertTitle, alertMetadata);

        log.info("📢 Alerts sent: ticketId={}, team={}", ticketId, team);
    }

    /**
     * Updates DLQ record with escalation information.
     */
    private void updateDLQRecord(DlqRecordEntity dlqRecord, EscalationTeam team, String ticketId) {
        dlqRecord.setStatus(DlqStatus.PARKED);
        dlqRecord.setParkedAt(LocalDateTime.now());
        dlqRecord.setParkedReason(String.format("Escalated for manual review - Team: %s, Ticket: %s",
                team.name(), ticketId));

        dlqRecordRepository.save(dlqRecord);

        log.info("💾 DLQ record updated: messageId={}, status=PARKED", dlqRecord.getMessageId());
    }

    /**
     * Records metrics.
     */
    private void recordMetric(EscalationTeam team, String priority) {
        Counter.builder("dlq.manual_intervention.escalated")
            .tag("team", team.name())
            .tag("priority", priority)
            .tag("strategy", "manual_intervention")
            .description("DLQ messages escalated for manual intervention")
            .register(meterRegistry)
            .increment();
    }

    /**
     * Truncates string to max length.
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return "N/A";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "... (truncated)";
    }

    @Override
    public String getStrategyName() {
        return "MANUAL_INTERVENTION";
    }

    @Override
    public boolean canHandle(DlqRecordEntity dlqRecord) {
        // Can handle any message that has exceeded retry limits
        // or requires manual review
        return dlqRecord.getRetryCount() >= 5 ||
               dlqRecord.getStatus() == DlqStatus.PARKED;
    }

    /**
     * Escalation teams.
     */
    private enum EscalationTeam {
        FINANCE("Finance Team", "#finance-alerts"),
        COMPLIANCE("Compliance Team", "#compliance-alerts"),
        FRAUD("Fraud Team", "#fraud-alerts"),
        SECURITY("Security Team", "#security-alerts"),
        ENGINEERING("Engineering Team", "#engineering-alerts"),
        OPERATIONS("Operations Team", "#operations-alerts");

        private final String displayName;
        private final String slackChannel;

        EscalationTeam(String displayName, String slackChannel) {
            this.displayName = displayName;
            this.slackChannel = slackChannel;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getSlackChannel() {
            return slackChannel;
        }
    }
}
