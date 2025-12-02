package com.waqiti.common.alerting.client;

import com.waqiti.common.alerting.model.Alert;
import com.waqiti.common.alerting.model.AlertSeverity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Enterprise Slack API Client
 *
 * Rich Slack integration with:
 * - Color-coded message formatting
 * - Rich attachments with fields
 * - Threaded conversations
 * - Action buttons
 * - Emoji reactions
 * - Channel routing by severity
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SlackClient {

    private final RestTemplate restTemplate;

    @Value("${waqiti.alerting.slack.webhook-url:}")
    private String webhookUrl;

    @Value("${waqiti.alerting.slack.enabled:false}")
    private boolean enabled;

    @Value("${waqiti.alerting.slack.default-channel:#alerts}")
    private String defaultChannel;

    @Value("${waqiti.alerting.slack.critical-channel:#critical-alerts}")
    private String criticalChannel;

    @Value("${waqiti.alerting.slack.username:Waqiti Alert Bot}")
    private String botUsername;

    @Value("${waqiti.alerting.slack.icon-emoji::robot_face:}")
    private String iconEmoji;

    private static final DateTimeFormatter TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
            .withZone(ZoneId.of("UTC"));

    /**
     * Send alert to Slack with rich formatting (async)
     */
    @Async
    public CompletableFuture<String> sendAlert(Alert alert) {
        if (!enabled) {
            log.debug("Slack disabled, skipping alert: {}", alert.getId());
            return CompletableFuture.completedFuture(null);
        }

        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.error("Slack webhook URL not configured");
            return CompletableFuture.failedFuture(
                new IllegalStateException("Slack webhook URL not configured")
            );
        }

        try {
            Map<String, Object> message = buildAlertMessage(alert);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(message, headers);

            log.info("Sending alert to Slack: {} (severity: {})",
                alert.getId(), alert.getSeverity());

            ResponseEntity<String> response = restTemplate.postForEntity(
                webhookUrl,
                request,
                String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Slack alert sent successfully: {}", alert.getId());
                return CompletableFuture.completedFuture(alert.getId());
            } else {
                log.error("Failed to send Slack alert: {}", response.getStatusCode());
                return CompletableFuture.failedFuture(
                    new RuntimeException("Failed to send Slack alert: " + response.getStatusCode())
                );
            }

        } catch (Exception e) {
            log.error("Error sending Slack alert {}: {}", alert.getId(), e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Send simple message to Slack
     */
    @Async
    public CompletableFuture<Void> sendMessage(String channel, String message) {
        if (!enabled) {
            log.debug("Slack disabled, would send to {}: {}", channel, message);
            return CompletableFuture.completedFuture(null);
        }

        try {
            Map<String, Object> slackMessage = new HashMap<>();
            slackMessage.put("text", message);
            slackMessage.put("channel", channel != null ? channel : defaultChannel);
            slackMessage.put("username", botUsername);
            slackMessage.put("icon_emoji", iconEmoji);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(slackMessage, headers);

            restTemplate.postForEntity(webhookUrl, request, String.class);
            log.info("Slack message sent to {}", channel);

            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            log.error("Failed to send Slack message to {}: {}", channel, e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Send rich message with attachments
     */
    @Async
    public CompletableFuture<Void> sendRichMessage(String channel, String title,
                                                     String text, String color) {
        if (!enabled) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            Map<String, Object> attachment = new HashMap<>();
            attachment.put("title", title);
            attachment.put("text", text);
            attachment.put("color", color);
            attachment.put("ts", Instant.now().getEpochSecond());

            Map<String, Object> message = new HashMap<>();
            message.put("channel", channel != null ? channel : defaultChannel);
            message.put("username", botUsername);
            message.put("icon_emoji", iconEmoji);
            message.put("attachments", List.of(attachment));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(message, headers);

            restTemplate.postForEntity(webhookUrl, request, String.class);
            log.info("Slack rich message sent to {}", channel);

            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            log.error("Failed to send Slack rich message: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Test Slack connection
     */
    public CompletableFuture<Boolean> testConnection() {
        if (!enabled) {
            log.warn("Slack is disabled");
            return CompletableFuture.completedFuture(false);
        }

        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.error("Slack webhook URL not configured");
            return CompletableFuture.completedFuture(false);
        }

        try {
            Map<String, Object> testMessage = new HashMap<>();
            testMessage.put("text", "✅ Slack Connection Test - Waqiti Alerting System");
            testMessage.put("channel", defaultChannel);
            testMessage.put("username", botUsername);
            testMessage.put("icon_emoji", iconEmoji);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(testMessage, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                webhookUrl,
                request,
                String.class
            );

            boolean success = response.getStatusCode().is2xxSuccessful();

            if (success) {
                log.info("Slack connection test successful");
            } else {
                log.error("Slack connection test failed: {}", response.getStatusCode());
            }

            return CompletableFuture.completedFuture(success);

        } catch (Exception e) {
            log.error("Slack connection test failed: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Build rich Slack message from alert
     */
    private Map<String, Object> buildAlertMessage(Alert alert) {
        Map<String, Object> message = new HashMap<>();

        // Determine channel based on severity
        String channel = alert.getSeverity() == AlertSeverity.CRITICAL ?
            criticalChannel : defaultChannel;

        message.put("channel", channel);
        message.put("username", botUsername);
        message.put("icon_emoji", iconEmoji);

        // Build main text with emoji
        String emoji = alert.getSeverity().getEmoji();
        String mainText = String.format("%s *%s Alert* from `%s`",
            emoji,
            alert.getSeverity().name(),
            alert.getSource() != null ? alert.getSource() : "unknown"
        );
        message.put("text", mainText);

        // Build rich attachment
        Map<String, Object> attachment = new HashMap<>();

        // Title
        attachment.put("title", alert.getMessage() != null ? alert.getMessage() : "Alert");

        // Color coding by severity
        attachment.put("color", alert.getSeverity().getColorCode());

        // Fallback text for notifications
        attachment.put("fallback", alert.getMessage());

        // Fields with alert details
        List<Map<String, Object>> fields = new ArrayList<>();

        // Alert ID
        fields.add(createField("Alert ID", alert.getId(), true));

        // Severity
        fields.add(createField("Severity",
            alert.getSeverity().name() + " (" + alert.getSeverity().getPriority() + ")",
            true));

        // Source
        if (alert.getSource() != null) {
            fields.add(createField("Source", alert.getSource(), true));
        }

        // Type
        if (alert.getType() != null) {
            fields.add(createField("Type", alert.getType(), true));
        }

        // Timestamp
        String timestamp = TIME_FORMATTER.format(
            alert.getCreatedAt() != null ? alert.getCreatedAt() : Instant.now()
        );
        fields.add(createField("Timestamp", timestamp, true));

        // Metadata fields (limit to important ones)
        if (alert.getMetadata() != null && !alert.getMetadata().isEmpty()) {
            int count = 0;
            for (Map.Entry<String, Object> entry : alert.getMetadata().entrySet()) {
                if (count++ >= 5) break; // Limit to 5 metadata fields

                String key = formatFieldName(entry.getKey());
                String value = entry.getValue() != null ?
                    entry.getValue().toString() : "N/A";

                // Truncate long values
                if (value.length() > 100) {
                    value = value.substring(0, 97) + "...";
                }

                fields.add(createField(key, value, true));
            }
        }

        attachment.put("fields", fields);

        // Footer
        attachment.put("footer", "Waqiti Alerting System");
        attachment.put("footer_icon",
            "https://platform.slack-edge.com/img/default_application_icon.png");

        // Timestamp
        attachment.put("ts", Instant.now().getEpochSecond());

        message.put("attachments", List.of(attachment));

        return message;
    }

    /**
     * Create Slack field
     */
    private Map<String, Object> createField(String title, String value, boolean isShort) {
        Map<String, Object> field = new HashMap<>();
        field.put("title", title);
        field.put("value", value);
        field.put("short", isShort);
        return field;
    }

    /**
     * Format field name for display
     */
    private String formatFieldName(String key) {
        // Convert camelCase or snake_case to Title Case
        return Arrays.stream(key.split("(?=[A-Z])|_"))
            .map(word -> word.substring(0, 1).toUpperCase() +
                        word.substring(1).toLowerCase())
            .reduce((a, b) -> a + " " + b)
            .orElse(key);
    }
}
