package com.waqiti.common.alerting;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ✅ CRITICAL PRODUCTION FIX: Slack Integration Service
 *
 * Provides real-time alerting to Slack channels for operational awareness.
 * Supports multiple channels for different teams and severity levels.
 *
 * BUSINESS IMPACT:
 * - Real-time visibility for operations teams
 * - Faster incident response times
 * - Team collaboration during incidents
 * - Audit trail of system events
 *
 * SUPPORTED CHANNELS:
 * - #critical-alerts: P0/P1 incidents requiring immediate action
 * - #finance-ops: Financial discrepancies, reconciliation failures
 * - #engineering-alerts: System errors, performance issues
 * - #compliance: AML, sanctions, regulatory events
 * - #risk-ops: Fraud detection, suspicious activity
 * - #security-ops: Security incidents, unauthorized access
 *
 * @author Waqiti Engineering Team
 * @since 2025-01-16
 * @version 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SlackService {

    @Value("${slack.webhook.critical-alerts:#{null}}")
    private String criticalAlertsWebhook;

    @Value("${slack.webhook.finance-ops:#{null}}")
    private String financeOpsWebhook;

    @Value("${slack.webhook.engineering-alerts:#{null}}")
    private String engineeringAlertsWebhook;

    @Value("${slack.webhook.compliance:#{null}}")
    private String complianceWebhook;

    @Value("${slack.webhook.risk-ops:#{null}}")
    private String riskOpsWebhook;

    @Value("${slack.webhook.security-ops:#{null}}")
    private String securityOpsWebhook;

    @Value("${slack.enabled:false}")
    private boolean enabled;

    private final RestTemplate restTemplate;

    /**
     * Send alert to specified Slack channel
     *
     * @param channel Channel name (e.g., "#critical-alerts")
     * @param severity P0/P1/P2 or CRITICAL/HIGH/MEDIUM
     * @param title Alert title
     * @param message Alert message
     * @param details Additional context
     */
    public void sendAlert(String channel, String severity, String title, String message,
                         Map<String, Object> details) {
        if (!enabled) {
            log.warn("Slack is disabled - Alert not sent to {}: {}", channel, title);
            return;
        }

        String webhookUrl = getWebhookUrl(channel);
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.error("Slack webhook not configured for channel: {} - Alert not sent: {}", channel, title);
            return;
        }

        try {
            SlackMessage slackMessage = SlackMessage.builder()
                .username("Waqiti Alerts")
                .iconEmoji(getSeverityEmoji(severity))
                .attachments(List.of(
                    SlackAttachment.builder()
                        .color(getSeverityColor(severity))
                        .title(String.format("[%s] %s", severity.toUpperCase(), title))
                        .text(message)
                        .fields(buildFields(details))
                        .footer("Waqiti Platform | " + getCurrentEnvironment())
                        .ts(Instant.now().getEpochSecond())
                        .build()
                ))
                .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<SlackMessage> request = new HttpEntity<>(slackMessage, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                webhookUrl, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Slack alert sent successfully to {}: severity={}, title={}",
                    channel, severity, title);
            } else {
                log.error("Slack alert failed: channel={}, status={}, title={}",
                    channel, response.getStatusCode(), title);
            }

        } catch (Exception e) {
            log.error("Failed to send Slack alert to {}: title={}, error={}",
                channel, title, e.getMessage(), e);
            // Don't fail the calling operation - alerting is best-effort
        }
    }

    /**
     * Send critical alert (P0) to multiple channels
     */
    public void sendCriticalAlert(String title, String message, Map<String, Object> details) {
        sendAlert("#critical-alerts", "P0", title, message, details);

        // Also notify appropriate team channel based on details
        String component = String.valueOf(details.getOrDefault("component", ""));

        if (component.contains("payment") || component.contains("settlement")) {
            sendAlert("#finance-ops", "P0", title, message, details);
        } else if (component.contains("fraud") || component.contains("risk")) {
            sendAlert("#risk-ops", "P0", title, message, details);
        } else if (component.contains("compliance") || component.contains("kyc")) {
            sendAlert("#compliance", "P0", title, message, details);
        } else if (component.contains("security") || component.contains("auth")) {
            sendAlert("#security-ops", "P0", title, message, details);
        }
    }

    /**
     * Send formatted financial alert
     */
    public void sendFinancialAlert(String severity, String title, String message,
                                   String amount, String currency, Map<String, Object> details) {
        String formattedMessage = String.format("%s\n\n*Amount:* %s %s", message, amount, currency);
        sendAlert("#finance-ops", severity, title, formattedMessage, details);
    }

    /**
     * Send fraud detection alert
     */
    public void sendFraudAlert(String severity, String title, String userId, String riskScore,
                              String reason, Map<String, Object> details) {
        String message = String.format(
            "*User ID:* %s\n*Risk Score:* %s\n*Reason:* %s",
            userId, riskScore, reason
        );
        sendAlert("#risk-ops", severity, title, message, details);

        // If high severity, also alert security team
        if ("P0".equals(severity) || "CRITICAL".equalsIgnoreCase(severity)) {
            sendAlert("#security-ops", severity, title, message, details);
        }
    }

    /**
     * Send compliance alert
     */
    public void sendComplianceAlert(String severity, String title, String message,
                                   Map<String, Object> details) {
        sendAlert("#compliance", severity, title, message, details);

        // For critical compliance issues, also alert executive team
        if ("P0".equals(severity) || "CRITICAL".equalsIgnoreCase(severity)) {
            sendAlert("#critical-alerts", severity, title, message, details);
        }
    }

    /**
     * Get webhook URL for channel
     */
    private String getWebhookUrl(String channel) {
        return switch (channel.toLowerCase()) {
            case "#critical-alerts" -> criticalAlertsWebhook;
            case "#finance-ops" -> financeOpsWebhook;
            case "#engineering-alerts" -> engineeringAlertsWebhook;
            case "#compliance" -> complianceWebhook;
            case "#risk-ops" -> riskOpsWebhook;
            case "#security-ops" -> securityOpsWebhook;
            default -> criticalAlertsWebhook; // Fallback to critical alerts
        };
    }

    /**
     * Get color for severity level
     */
    private String getSeverityColor(String severity) {
        if (severity == null) return "#CCCCCC";

        return switch (severity.toUpperCase()) {
            case "P0", "CRITICAL" -> "#FF0000";  // Red
            case "P1", "HIGH" -> "#FFA500";      // Orange
            case "P2", "MEDIUM" -> "#FFFF00";    // Yellow
            case "P3", "LOW" -> "#36A64F";       // Green
            default -> "#CCCCCC";                // Gray
        };
    }

    /**
     * Get emoji for severity level
     */
    private String getSeverityEmoji(String severity) {
        if (severity == null) return ":information_source:";

        return switch (severity.toUpperCase()) {
            case "P0", "CRITICAL" -> ":rotating_light:";
            case "P1", "HIGH" -> ":warning:";
            case "P2", "MEDIUM" -> ":large_orange_diamond:";
            case "P3", "LOW" -> ":white_check_mark:";
            default -> ":information_source:";
        };
    }

    /**
     * Build Slack message fields from details map
     */
    private List<SlackField> buildFields(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return List.of();
        }

        List<SlackField> fields = new ArrayList<>();

        // Add important fields first
        addFieldIfPresent(fields, details, "paymentId", "Payment ID", true);
        addFieldIfPresent(fields, details, "transactionId", "Transaction ID", true);
        addFieldIfPresent(fields, details, "walletId", "Wallet ID", true);
        addFieldIfPresent(fields, details, "userId", "User ID", true);
        addFieldIfPresent(fields, details, "amount", "Amount", true);
        addFieldIfPresent(fields, details, "currency", "Currency", true);
        addFieldIfPresent(fields, details, "errorMessage", "Error Message", false);
        addFieldIfPresent(fields, details, "timestamp", "Timestamp", true);

        // Add remaining fields (max 20 total)
        for (Map.Entry<String, Object> entry : details.entrySet()) {
            if (fields.size() >= 20) break;

            String key = entry.getKey();
            if (!isFieldAdded(fields, key)) {
                fields.add(SlackField.builder()
                    .title(formatFieldName(key))
                    .value(String.valueOf(entry.getValue()))
                    .shortField(shouldBeShortField(key))
                    .build());
            }
        }

        return fields;
    }

    /**
     * Add field if present in details
     */
    private void addFieldIfPresent(List<SlackField> fields, Map<String, Object> details,
                                   String key, String title, boolean shortField) {
        if (details.containsKey(key)) {
            Object value = details.get(key);
            if (value != null) {
                fields.add(SlackField.builder()
                    .title(title)
                    .value(maskSensitiveData(key, String.valueOf(value)))
                    .shortField(shortField)
                    .build());
            }
        }
    }

    /**
     * Check if field already added
     */
    private boolean isFieldAdded(List<SlackField> fields, String key) {
        String formattedKey = formatFieldName(key);
        return fields.stream().anyMatch(f -> f.getTitle().equals(formattedKey));
    }

    /**
     * Format field name for display
     */
    private String formatFieldName(String key) {
        // Convert camelCase to Title Case
        return key.replaceAll("([A-Z])", " $1")
            .replaceAll("([a-z])([A-Z])", "$1 $2")
            .trim()
            .substring(0, 1).toUpperCase() + key.substring(1);
    }

    /**
     * Determine if field should be short (side-by-side)
     */
    private boolean shouldBeShortField(String key) {
        return key.length() < 20 && !key.toLowerCase().contains("message") &&
               !key.toLowerCase().contains("description") && !key.toLowerCase().contains("reason");
    }

    /**
     * Mask sensitive data in field values
     */
    private String maskSensitiveData(String key, String value) {
        String lowerKey = key.toLowerCase();

        if (lowerKey.contains("password") || lowerKey.contains("token") ||
            lowerKey.contains("secret") || lowerKey.contains("apikey")) {
            return "***REDACTED***";
        }

        if (lowerKey.contains("email")) {
            return maskEmail(value);
        }

        if (lowerKey.contains("cardnumber") || lowerKey.contains("card_number")) {
            return "****" + value.substring(Math.max(0, value.length() - 4));
        }

        return value;
    }

    /**
     * Mask email address
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***@***";

        String[] parts = email.split("@");
        String localPart = parts[0];
        String domain = parts[1];

        if (localPart.length() <= 2) {
            return "**@" + domain;
        }

        return localPart.substring(0, 2) + "***@" + domain;
    }

    /**
     * Get current environment
     */
    private String getCurrentEnvironment() {
        String env = System.getenv("ENVIRONMENT");
        return env != null ? env.toUpperCase() : "PRODUCTION";
    }

    // DTOs for Slack Incoming Webhooks

    @Data
    @Builder
    public static class SlackMessage {
        private String username;
        @JsonProperty("icon_emoji")
        private String iconEmoji;
        private List<SlackAttachment> attachments;
    }

    @Data
    @Builder
    public static class SlackAttachment {
        private String color;
        private String title;
        private String text;
        private List<SlackField> fields;
        private String footer;
        private Long ts;
    }

    @Data
    @Builder
    public static class SlackField {
        private String title;
        private String value;
        @JsonProperty("short")
        private Boolean shortField;
    }
}
