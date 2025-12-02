package com.waqiti.ledger.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Slack Notification Service
 *
 * Sends notifications to Slack channels for operational alerts,
 * reconciliation issues, and team coordination.
 *
 * Channels:
 * - #finance-ops: Operational finance alerts
 * - #accounting: Month-end, reconciliation, compliance
 * - #compliance: Regulatory and audit alerts
 * - #engineering-alerts: Technical issues affecting ledger
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlackNotificationService {

    private final RestTemplate restTemplate;

    @Value("${waqiti.slack.webhook-url:}")
    private String slackWebhookUrl;

    @Value("${waqiti.slack.enabled:true}")
    private boolean enabled;

    // Channel-specific webhooks (configured via environment/Vault)
    @Value("${waqiti.slack.finance-ops-webhook:}")
    private String financeOpsWebhook;

    @Value("${waqiti.slack.accounting-webhook:}")
    private String accountingWebhook;

    @Value("${waqiti.slack.compliance-webhook:}")
    private String complianceWebhook;

    /**
     * Sends a notification to a Slack channel
     *
     * @param channel Channel name (e.g., "#finance-ops")
     * @param message Message to send
     */
    public void sendNotification(String channel, String message) {
        sendNotification(channel, message, null);
    }

    /**
     * Sends a notification with additional context
     *
     * @param channel Channel name
     * @param message Main message
     * @param details Additional details
     */
    public void sendNotification(String channel, String message, Map<String, String> details) {
        if (!enabled) {
            log.warn("Slack is disabled. Notification not sent to {}: {}", channel, message);
            return;
        }

        String webhookUrl = getWebhookForChannel(channel);
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.error("Slack webhook not configured for channel: {}. Message: {}", channel, message);
            return;
        }

        try {
            Map<String, Object> payload = buildPayload(channel, message, details, "info");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    webhookUrl,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Slack notification sent to {} - Status: {}", channel, response.getStatusCode());
            } else {
                log.error("❌ Slack notification failed to {} - Status: {}, Response: {}",
                        channel, response.getStatusCode(), response.getBody());
            }

        } catch (Exception e) {
            log.error("❌ Failed to send Slack notification to {} - Message: {}, Error: {}",
                    channel, message, e.getMessage(), e);
        }
    }

    /**
     * Sends a critical alert with high visibility (red color, @channel mention)
     */
    public void sendCriticalAlert(String channel, String message, Map<String, String> details) {
        if (!enabled) {
            log.warn("Slack is disabled. Critical alert not sent to {}: {}", channel, message);
            return;
        }

        String webhookUrl = getWebhookForChannel(channel);
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.error("Slack webhook not configured for channel: {}. Critical alert: {}", channel, message);
            return;
        }

        try {
            Map<String, Object> payload = buildPayload(channel, "<!channel> 🚨 CRITICAL ALERT\n" + message, details, "danger");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    webhookUrl,
                    request,
                    String.class
            );

            log.info("✅ Slack CRITICAL alert sent to {} - Status: {}", channel, response.getStatusCode());

        } catch (Exception e) {
            log.error("❌ Failed to send Slack CRITICAL alert to {} - Error: {}", channel, e.getMessage(), e);
        }
    }

    /**
     * Sends a warning alert (orange color)
     */
    public void sendWarningAlert(String channel, String message, Map<String, String> details) {
        if (!enabled) {
            return;
        }

        String webhookUrl = getWebhookForChannel(channel);
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return;
        }

        try {
            Map<String, Object> payload = buildPayload(channel, "⚠️ WARNING\n" + message, details, "warning");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            restTemplate.postForEntity(webhookUrl, request, String.class);

        } catch (Exception e) {
            log.error("Failed to send Slack warning to {}: {}", channel, e.getMessage());
        }
    }

    /**
     * Sends a success notification (green color)
     */
    public void sendSuccessNotification(String channel, String message, Map<String, String> details) {
        if (!enabled) {
            return;
        }

        String webhookUrl = getWebhookForChannel(channel);
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return;
        }

        try {
            Map<String, Object> payload = buildPayload(channel, "✅ " + message, details, "good");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            restTemplate.postForEntity(webhookUrl, request, String.class);

        } catch (Exception e) {
            log.error("Failed to send Slack success notification to {}: {}", channel, e.getMessage());
        }
    }

    /**
     * Builds Slack message payload with rich formatting
     */
    private Map<String, Object> buildPayload(String channel, String message, Map<String, String> details, String color) {
        Map<String, Object> payload = new HashMap<>();

        // Fallback text for notifications
        payload.put("text", message);

        // Rich formatting with attachments
        List<Map<String, Object>> attachments = new ArrayList<>();
        Map<String, Object> attachment = new HashMap<>();

        attachment.put("color", color);
        attachment.put("text", message);
        attachment.put("footer", "Waqiti Ledger Service");
        attachment.put("footer_icon", "https://api.example.com/favicon.ico");
        attachment.put("ts", System.currentTimeMillis() / 1000);

        // Add fields if details provided
        if (details != null && !details.isEmpty()) {
            List<Map<String, String>> fields = new ArrayList<>();
            for (Map.Entry<String, String> entry : details.entrySet()) {
                Map<String, String> field = new HashMap<>();
                field.put("title", entry.getKey());
                field.put("value", entry.getValue());
                field.put("short", "true");
                fields.add(field);
            }
            attachment.put("fields", fields);
        }

        attachments.add(attachment);
        payload.put("attachments", attachments);

        return payload;
    }

    /**
     * Gets the appropriate webhook URL for a channel
     */
    private String getWebhookForChannel(String channel) {
        switch (channel.toLowerCase()) {
            case "#finance-ops":
            case "finance-ops":
                return financeOpsWebhook != null && !financeOpsWebhook.isEmpty() ? financeOpsWebhook : slackWebhookUrl;

            case "#accounting":
            case "accounting":
                return accountingWebhook != null && !accountingWebhook.isEmpty() ? accountingWebhook : slackWebhookUrl;

            case "#compliance":
            case "compliance":
                return complianceWebhook != null && !complianceWebhook.isEmpty() ? complianceWebhook : slackWebhookUrl;

            default:
                return slackWebhookUrl;
        }
    }

    /**
     * Sends formatted reconciliation alert
     */
    public void sendReconciliationAlert(String channel, String accountId, String discrepancy, String severity) {
        Map<String, String> details = new HashMap<>();
        details.put("Account ID", accountId);
        details.put("Discrepancy", discrepancy);
        details.put("Severity", severity);
        details.put("Time", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        String message = String.format("Reconciliation discrepancy detected for account %s", accountId);

        if ("CRITICAL".equals(severity) || "HIGH".equals(severity)) {
            sendCriticalAlert(channel, message, details);
        } else {
            sendWarningAlert(channel, message, details);
        }
    }
}
