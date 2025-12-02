package com.waqiti.alerting.service;

import com.waqiti.alerting.dto.AlertContext;
import com.waqiti.alerting.dto.AlertSeverity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enterprise-Grade Slack Alert Service
 *
 * Sends real-time alerts to Slack channels for fraud analysts, compliance officers,
 * and engineering teams. Provides rich formatting with actionable buttons.
 *
 * Features:
 * - Severity-based channel routing
 * - Rich message formatting with blocks
 * - Action buttons for common workflows
 * - Thread support for related alerts
 * - Mention support for urgent alerts
 *
 * @author Waqiti Platform Team
 * @version 2.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SlackAlertService {

    private final RestTemplate restTemplate;

    @Value("${slack.webhook.fraud-alerts}")
    private String fraudAlertsWebhook;

    @Value("${slack.webhook.compliance-alerts}")
    private String complianceAlertsWebhook;

    @Value("${slack.webhook.engineering-alerts}")
    private String engineeringAlertsWebhook;

    @Value("${slack.webhook.payments-alerts}")
    private String paymentsAlertsWebhook;

    @Value("${slack.enabled:true}")
    private boolean slackEnabled;

    @Value("${slack.mention.fraud-team:@fraud-analysts}")
    private String fraudTeamMention;

    @Value("${slack.mention.compliance-team:@compliance-officers}")
    private String complianceTeamMention;

    /**
     * Sends fraud alert to Slack #fraud-alerts channel
     */
    public void sendFraudAlert(String userId, String transactionId, double riskScore,
                               String riskFactors, Map<String, Object> transactionDetails) {
        if (!slackEnabled) {
            log.warn("Slack is disabled. Fraud alert would have been sent for transaction: {}", transactionId);
            return;
        }

        try {
            AlertSeverity severity = riskScore > 0.9 ? AlertSeverity.CRITICAL : AlertSeverity.HIGH;
            String emoji = getEmojiForSeverity(severity);
            String color = getColorForSeverity(severity);

            Map<String, Object> message = new HashMap<>();
            message.put("text", String.format("%s *FRAUD ALERT* - High-Risk Transaction Detected", emoji));

            List<Map<String, Object>> blocks = new ArrayList<>();

            // Header
            blocks.add(createHeaderBlock(String.format("%s FRAUD ALERT: High-Risk Transaction", emoji)));

            // Risk score section
            blocks.add(createSectionBlock(
                String.format("*Risk Score:* %.1f%% | *User:* `%s` | *Transaction:* `%s`",
                    riskScore * 100, userId, transactionId),
                null
            ));

            // Risk factors
            blocks.add(createSectionBlock(String.format("*Risk Factors:*\n%s", riskFactors), null));

            // Transaction details
            StringBuilder detailsText = new StringBuilder("*Transaction Details:*\n");
            transactionDetails.forEach((key, value) ->
                detailsText.append(String.format("• %s: `%s`\n", formatKey(key), value))
            );
            blocks.add(createSectionBlock(detailsText.toString(), null));

            // Action buttons
            blocks.add(createActionsBlock(transactionId, userId));

            // Divider
            blocks.add(createDivider());

            // Footer with timestamp
            blocks.add(createContextBlock(
                String.format("Detected at %s | Requires immediate analyst review %s",
                    formatTimestamp(Instant.now()), fraudTeamMention)
            ));

            message.put("blocks", blocks);

            // Add attachment for color bar
            List<Map<String, Object>> attachments = new ArrayList<>();
            Map<String, Object> attachment = new HashMap<>();
            attachment.put("color", color);
            attachments.add(attachment);
            message.put("attachments", attachments);

            sendToSlack(fraudAlertsWebhook, message);

            log.info("Fraud alert sent to Slack for transaction: {} [Risk: {:.1f}%]",
                transactionId, riskScore * 100);

        } catch (Exception e) {
            log.error("Failed to send fraud alert to Slack for transaction: {}", transactionId, e);
        }
    }

    /**
     * Sends compliance alert to Slack #compliance-alerts channel
     */
    public void sendComplianceAlert(String userId, String transactionId, String issueType,
                                    String description, AlertSeverity severity) {
        if (!slackEnabled) {
            log.warn("Slack is disabled. Compliance alert would have been sent: {}", issueType);
            return;
        }

        try {
            String emoji = getEmojiForSeverity(severity);
            String color = getColorForSeverity(severity);

            Map<String, Object> message = new HashMap<>();
            message.put("text", String.format("%s *COMPLIANCE ALERT* - %s", emoji, issueType));

            List<Map<String, Object>> blocks = new ArrayList<>();

            blocks.add(createHeaderBlock(String.format("%s COMPLIANCE: %s", emoji, issueType)));

            blocks.add(createSectionBlock(
                String.format("*User:* `%s`\n*Transaction:* `%s`\n*Issue:* %s",
                    userId, transactionId, issueType),
                null
            ));

            blocks.add(createSectionBlock(String.format("*Description:*\n%s", description), null));

            // Compliance-specific actions
            blocks.add(createComplianceActionsBlock(transactionId, userId, issueType));

            blocks.add(createDivider());

            blocks.add(createContextBlock(
                String.format("Detected at %s | %s requires review %s",
                    formatTimestamp(Instant.now()), issueType, complianceTeamMention)
            ));

            message.put("blocks", blocks);

            List<Map<String, Object>> attachments = new ArrayList<>();
            Map<String, Object> attachment = new HashMap<>();
            attachment.put("color", color);
            attachments.add(attachment);
            message.put("attachments", attachments);

            sendToSlack(complianceAlertsWebhook, message);

            log.info("Compliance alert sent to Slack: {} for transaction: {}", issueType, transactionId);

        } catch (Exception e) {
            log.error("Failed to send compliance alert to Slack: {}", issueType, e);
        }
    }

    /**
     * Sends SAR filing alert to compliance team
     */
    public void sendSARFilingAlert(String userId, String transactionId, String suspiciousActivity,
                                   Map<String, Object> details) {
        sendComplianceAlert(
            userId,
            transactionId,
            "SAR Filing Required",
            String.format("Suspicious Activity Report (SAR) must be filed within 30 days.\n\n" +
                "*Activity:* %s\n\n*Details:* %s",
                suspiciousActivity, formatDetails(details)),
            AlertSeverity.CRITICAL
        );
    }

    /**
     * Sends DLQ failure alert to engineering team
     */
    public void sendDLQFailureAlert(String topic, String messageId, String errorDetails,
                                    Map<String, Object> payload) {
        if (!slackEnabled) {
            log.warn("Slack is disabled. DLQ alert would have been sent for topic: {}", topic);
            return;
        }

        try {
            Map<String, Object> message = new HashMap<>();
            message.put("text", String.format(":rotating_light: *DLQ FAILURE* - Topic: %s", topic));

            List<Map<String, Object>> blocks = new ArrayList<>();

            blocks.add(createHeaderBlock(":rotating_light: Dead Letter Queue Failure"));

            blocks.add(createSectionBlock(
                String.format("*Topic:* `%s`\n*Message ID:* `%s`\n*Status:* Failed to process",
                    topic, messageId),
                null
            ));

            blocks.add(createSectionBlock(
                String.format("*Error Details:*\n```%s```", errorDetails),
                null
            ));

            if (payload != null && !payload.isEmpty()) {
                blocks.add(createSectionBlock(
                    String.format("*Payload Preview:*\n```%s```",
                        formatPayload(payload)),
                    null
                ));
            }

            blocks.add(createDivider());

            blocks.add(createContextBlock(
                String.format("Detected at %s | @engineering-oncall requires immediate attention",
                    formatTimestamp(Instant.now()))
            ));

            message.put("blocks", blocks);

            List<Map<String, Object>> attachments = new ArrayList<>();
            Map<String, Object> attachment = new HashMap<>();
            attachment.put("color", "#d00000");
            attachments.add(attachment);
            message.put("attachments", attachments);

            sendToSlack(engineeringAlertsWebhook, message);

            log.info("DLQ failure alert sent to Slack for topic: {}", topic);

        } catch (Exception e) {
            log.error("Failed to send DLQ alert to Slack for topic: {}", topic, e);
        }
    }

    /**
     * Sends payment failure alert to payments team
     */
    public void sendPaymentFailureAlert(String userId, String paymentId, String reason,
                                        Map<String, Object> paymentDetails) {
        if (!slackEnabled) {
            log.warn("Slack is disabled. Payment failure alert would have been sent: {}", paymentId);
            return;
        }

        try {
            Map<String, Object> message = new HashMap<>();
            message.put("text", String.format(":warning: Payment Failure - ID: %s", paymentId));

            List<Map<String, Object>> blocks = new ArrayList<>();

            blocks.add(createHeaderBlock(":warning: Payment Processing Failure"));

            blocks.add(createSectionBlock(
                String.format("*User:* `%s`\n*Payment ID:* `%s`\n*Reason:* %s",
                    userId, paymentId, reason),
                null
            ));

            if (paymentDetails != null && !paymentDetails.isEmpty()) {
                StringBuilder detailsText = new StringBuilder("*Payment Details:*\n");
                paymentDetails.forEach((key, value) ->
                    detailsText.append(String.format("• %s: `%s`\n", formatKey(key), value))
                );
                blocks.add(createSectionBlock(detailsText.toString(), null));
            }

            blocks.add(createDivider());

            blocks.add(createContextBlock(
                String.format("Failed at %s | @payments-team",
                    formatTimestamp(Instant.now()))
            ));

            message.put("blocks", blocks);

            List<Map<String, Object>> attachments = new ArrayList<>();
            Map<String, Object> attachment = new HashMap<>();
            attachment.put("color", "#ff9500");
            attachments.add(attachment);
            message.put("attachments", attachments);

            sendToSlack(paymentsAlertsWebhook, message);

            log.info("Payment failure alert sent to Slack: {}", paymentId);

        } catch (Exception e) {
            log.error("Failed to send payment alert to Slack: {}", paymentId, e);
        }
    }

    // ==================== Helper Methods ====================

    private void sendToSlack(String webhookUrl, Map<String, Object> message) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(message, headers);
        restTemplate.postForEntity(webhookUrl, request, String.class);
    }

    private Map<String, Object> createHeaderBlock(String text) {
        Map<String, Object> block = new HashMap<>();
        block.put("type", "header");
        block.put("text", Map.of("type", "plain_text", "text", text, "emoji", true));
        return block;
    }

    private Map<String, Object> createSectionBlock(String text, String accessory) {
        Map<String, Object> block = new HashMap<>();
        block.put("type", "section");
        block.put("text", Map.of("type", "mrkdwn", "text", text));
        if (accessory != null) {
            block.put("accessory", accessory);
        }
        return block;
    }

    private Map<String, Object> createActionsBlock(String transactionId, String userId) {
        Map<String, Object> block = new HashMap<>();
        block.put("type", "actions");

        List<Map<String, Object>> elements = new ArrayList<>();

        // Approve button
        elements.add(Map.of(
            "type", "button",
            "text", Map.of("type", "plain_text", "text", "Approve", "emoji", true),
            "style", "primary",
            "value", transactionId,
            "action_id", "approve_transaction"
        ));

        // Reject button
        elements.add(Map.of(
            "type", "button",
            "text", Map.of("type", "plain_text", "text", "Reject", "emoji", true),
            "style", "danger",
            "value", transactionId,
            "action_id", "reject_transaction"
        ));

        // Review button
        elements.add(Map.of(
            "type", "button",
            "text", Map.of("type", "plain_text", "text", "Full Review", "emoji", true),
            "value", transactionId,
            "action_id", "review_transaction"
        ));

        block.put("elements", elements);
        return block;
    }

    private Map<String, Object> createComplianceActionsBlock(String transactionId, String userId, String issueType) {
        Map<String, Object> block = new HashMap<>();
        block.put("type", "actions");

        List<Map<String, Object>> elements = new ArrayList<>();

        if ("SAR Filing Required".equals(issueType)) {
            elements.add(Map.of(
                "type", "button",
                "text", Map.of("type", "plain_text", "text", "Create SAR", "emoji", true),
                "style", "primary",
                "value", transactionId,
                "action_id", "create_sar"
            ));
        }

        elements.add(Map.of(
            "type", "button",
            "text", Map.of("type", "plain_text", "text", "View Details", "emoji", true),
            "value", transactionId,
            "action_id", "view_compliance_details"
        ));

        block.put("elements", elements);
        return block;
    }

    private Map<String, Object> createDivider() {
        return Map.of("type", "divider");
    }

    private Map<String, Object> createContextBlock(String text) {
        Map<String, Object> block = new HashMap<>();
        block.put("type", "context");
        block.put("elements", List.of(Map.of("type", "mrkdwn", "text", text)));
        return block;
    }

    private String getEmojiForSeverity(AlertSeverity severity) {
        return switch (severity) {
            case CRITICAL -> ":rotating_light:";
            case HIGH -> ":warning:";
            case MEDIUM -> ":information_source:";
            case LOW -> ":white_check_mark:";
        };
    }

    private String getColorForSeverity(AlertSeverity severity) {
        return switch (severity) {
            case CRITICAL -> "#d00000";
            case HIGH -> "#ff9500";
            case MEDIUM -> "#ffcc00";
            case LOW -> "#36c5f0";
        };
    }

    private String formatTimestamp(Instant instant) {
        return instant.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
    }

    private String formatKey(String key) {
        return key.replaceAll("([A-Z])", " $1")
            .replaceAll("_", " ")
            .trim()
            .substring(0, 1).toUpperCase() +
            key.replaceAll("([A-Z])", " $1")
                .replaceAll("_", " ")
                .trim()
                .substring(1);
    }

    private String formatDetails(Map<String, Object> details) {
        StringBuilder sb = new StringBuilder();
        details.forEach((key, value) ->
            sb.append(String.format("• %s: %s\n", formatKey(key), value))
        );
        return sb.toString();
    }

    private String formatPayload(Map<String, Object> payload) {
        // Truncate and format payload for display
        String json = payload.toString();
        return json.length() > 500 ? json.substring(0, 497) + "..." : json;
    }
}
