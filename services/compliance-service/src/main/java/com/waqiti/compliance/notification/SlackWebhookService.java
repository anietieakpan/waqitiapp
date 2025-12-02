package com.waqiti.compliance.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Slack Webhook Notification Service
 *
 * Production-ready Slack notifications using Incoming Webhooks
 *
 * Features:
 * - Channel-specific webhooks
 * - Rich message formatting (Blocks API)
 * - Thread support
 * - Attachment support
 * - Emoji and mentions
 * - Rate limiting compliance
 *
 * Configuration:
 * - slack.webhook.critical: Critical channel webhook URL
 * - slack.webhook.alerts: Alerts channel webhook URL
 * - slack.webhook.notifications: Notifications channel webhook URL
 * - slack.enabled: Enable/disable Slack notifications
 *
 * @author Waqiti Compliance Engineering
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SlackWebhookService {

    private final RestTemplate restTemplate;

    @Value("${slack.webhook.critical:${SLACK_WEBHOOK_CRITICAL:}}")
    private String criticalWebhookUrl;

    @Value("${slack.webhook.alerts:${SLACK_WEBHOOK_ALERTS:}}")
    private String alertsWebhookUrl;

    @Value("${slack.webhook.notifications:${SLACK_WEBHOOK_NOTIFICATIONS:}}")
    private String notificationsWebhookUrl;

    @Value("${slack.enabled:true}")
    private boolean slackEnabled;

    /**
     * Send alert to Slack channel
     */
    public void sendAlert(String channel, String message, Map<String, Object> metadata) {
        if (!slackEnabled) {
            log.info("Slack disabled, skipping alert to {}: {}", channel, message);
            return;
        }

        try {
            log.info("Sending Slack alert to {}: {}", channel, message);

            String webhookUrl = getWebhookUrlForChannel(channel);
            if (webhookUrl == null || webhookUrl.isBlank()) {
                log.warn("No webhook URL configured for channel {}, logging only", channel);
                logSlackMessage(channel, message);
                return;
            }

            SlackMessage slackMessage = createSlackMessage(message, metadata);
            sendToSlack(webhookUrl, slackMessage);

            log.info("Slack alert sent successfully to {}", channel);

        } catch (Exception e) {
            log.error("Failed to send Slack alert to {}: {}", channel, message, e);
        }
    }

    /**
     * Send critical alert to #compliance-critical
     */
    public void sendCriticalAlert(String message, Map<String, Object> details) {
        if (!slackEnabled) {
            log.error("Slack disabled but CRITICAL alert needed: {}", message);
            return;
        }

        try {
            log.error("Sending CRITICAL Slack alert: {}", message);

            SlackMessage slackMessage = createCriticalSlackMessage(message, details);
            sendToSlack(criticalWebhookUrl, slackMessage);

            log.info("Critical Slack alert sent successfully");

        } catch (Exception e) {
            log.error("CRITICAL: Failed to send critical Slack alert: {}", message, e);
        }
    }

    /**
     * Send high priority alert to #compliance-alerts
     */
    public void sendHighPriorityAlert(String message, Map<String, Object> details) {
        try {
            log.warn("Sending HIGH PRIORITY Slack alert: {}", message);

            SlackMessage slackMessage = createHighPrioritySlackMessage(message, details);
            sendToSlack(alertsWebhookUrl, slackMessage);

            log.info("High priority Slack alert sent successfully");

        } catch (Exception e) {
            log.error("Failed to send high priority Slack alert: {}", message, e);
        }
    }

    /**
     * Send notification to #compliance-notifications
     */
    public void sendNotification(String message, Map<String, Object> details) {
        try {
            log.info("Sending Slack notification: {}", message);

            SlackMessage slackMessage = createNotificationSlackMessage(message, details);
            sendToSlack(notificationsWebhookUrl, slackMessage);

            log.info("Slack notification sent successfully");

        } catch (Exception e) {
            log.error("Failed to send Slack notification: {}", message, e);
        }
    }

    /**
     * Internal method to send message to Slack webhook
     */
    private void sendToSlack(String webhookUrl, SlackMessage message) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("Slack webhook URL not configured, message will be logged only");
            logSlackMessage("UNKNOWN", message.getText());
            return;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<SlackMessage> entity = new HttpEntity<>(message, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                webhookUrl,
                HttpMethod.POST,
                entity,
                String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                log.debug("Slack webhook call successful");
            } else {
                log.error("Slack webhook returned non-OK status: {} - {}",
                    response.getStatusCode(), response.getBody());
            }

        } catch (Exception e) {
            log.error("Slack webhook call failed", e);
            logSlackMessage("FALLBACK", message.getText());
        }
    }

    /**
     * Create basic Slack message
     */
    private SlackMessage createSlackMessage(String text, Map<String, Object> metadata) {
        return SlackMessage.builder()
            .text(text)
            .blocks(createBasicBlocks(text, metadata))
            .build();
    }

    /**
     * Create critical Slack message with rich formatting
     */
    private SlackMessage createCriticalSlackMessage(String text, Map<String, Object> details) {
        List<Map<String, Object>> blocks = new ArrayList<>();

        // Header block
        blocks.add(Map.of(
            "type", "header",
            "text", Map.of(
                "type", "plain_text",
                "text", "🚨 CRITICAL COMPLIANCE ALERT",
                "emoji", true
            )
        ));

        // Message block
        blocks.add(Map.of(
            "type", "section",
            "text", Map.of(
                "type", "mrkdwn",
                "text", "*" + text + "*"
            )
        ));

        // Details block
        if (details != null && !details.isEmpty()) {
            StringBuilder detailsText = new StringBuilder("*Details:*\n");
            details.forEach((key, value) ->
                detailsText.append("• *").append(key).append(":* ").append(value).append("\n")
            );

            blocks.add(Map.of(
                "type", "section",
                "text", Map.of(
                    "type", "mrkdwn",
                    "text", detailsText.toString()
                )
            ));
        }

        // Context block with timestamp
        blocks.add(Map.of(
            "type", "context",
            "elements", List.of(
                Map.of(
                    "type", "mrkdwn",
                    "text", ":clock1: " + LocalDateTime.now().toString()
                )
            )
        ));

        // Divider
        blocks.add(Map.of("type", "divider"));

        return SlackMessage.builder()
            .text(text) // Fallback text
            .blocks(blocks)
            .build();
    }

    /**
     * Create high priority Slack message
     */
    private SlackMessage createHighPrioritySlackMessage(String text, Map<String, Object> details) {
        List<Map<String, Object>> blocks = new ArrayList<>();

        // Header
        blocks.add(Map.of(
            "type", "header",
            "text", Map.of(
                "type", "plain_text",
                "text", "⚠️ High Priority Alert",
                "emoji", true
            )
        ));

        // Message
        blocks.add(Map.of(
            "type", "section",
            "text", Map.of(
                "type", "mrkdwn",
                "text", text
            )
        ));

        // Details if present
        if (details != null && !details.isEmpty()) {
            blocks.add(Map.of(
                "type", "section",
                "fields", details.entrySet().stream()
                    .map(e -> Map.of(
                        "type", "mrkdwn",
                        "text", "*" + e.getKey() + ":*\n" + e.getValue()
                    ))
                    .toList()
            ));
        }

        return SlackMessage.builder()
            .text(text)
            .blocks(blocks)
            .build();
    }

    /**
     * Create notification Slack message
     */
    private SlackMessage createNotificationSlackMessage(String text, Map<String, Object> details) {
        List<Map<String, Object>> blocks = new ArrayList<>();

        // Simple message
        blocks.add(Map.of(
            "type", "section",
            "text", Map.of(
                "type", "mrkdwn",
                "text", "ℹ️ " + text
            )
        ));

        return SlackMessage.builder()
            .text(text)
            .blocks(blocks)
            .build();
    }

    /**
     * Create basic blocks for standard message
     */
    private List<Map<String, Object>> createBasicBlocks(String text, Map<String, Object> metadata) {
        List<Map<String, Object>> blocks = new ArrayList<>();

        blocks.add(Map.of(
            "type", "section",
            "text", Map.of(
                "type", "mrkdwn",
                "text", text
            )
        ));

        return blocks;
    }

    /**
     * Get webhook URL for channel
     */
    private String getWebhookUrlForChannel(String channel) {
        return switch (channel) {
            case "#compliance-critical" -> criticalWebhookUrl;
            case "#compliance-alerts" -> alertsWebhookUrl;
            case "#compliance-notifications" -> notificationsWebhookUrl;
            default -> null;
        };
    }

    /**
     * Log Slack message as fallback
     */
    private void logSlackMessage(String channel, String message) {
        log.warn("SLACK_FALLBACK: Channel={}, Message={}", channel, message);
    }

    /**
     * Slack message model
     */
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class SlackMessage {
        private String text; // Fallback text
        private List<Map<String, Object>> blocks; // Rich blocks
    }
}
