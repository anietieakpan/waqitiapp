package com.waqiti.account.kafka.dlq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of DLQ alert service
 *
 * <p>Integrates with:</p>
 * <ul>
 *   <li>PagerDuty - critical incidents requiring immediate response</li>
 *   <li>Slack - team notifications and warnings</li>
 *   <li>Email - compliance and audit notifications (future)</li>
 * </ul>
 *
 * <h3>Configuration Properties:</h3>
 * <pre>
 * dlq.alerts.pagerduty.enabled=true
 * dlq.alerts.pagerduty.integration-key=${PAGERDUTY_INTEGRATION_KEY}
 * dlq.alerts.slack.enabled=true
 * dlq.alerts.slack.webhook-url=${SLACK_WEBHOOK_URL}
 * dlq.alerts.slack.channel=#account-service-alerts
 * </pre>
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "dlq.alerts", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DlqAlertServiceImpl implements DlqAlertService {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${dlq.alerts.pagerduty.enabled:false}")
    private boolean pagerDutyEnabled;

    @Value("${dlq.alerts.pagerduty.integration-key:}")
    private String pagerDutyIntegrationKey;

    @Value("${dlq.alerts.pagerduty.api-url:https://events.pagerduty.com/v2/enqueue}")
    private String pagerDutyApiUrl;

    @Value("${dlq.alerts.slack.enabled:false}")
    private boolean slackEnabled;

    @Value("${dlq.alerts.slack.webhook-url:}")
    private String slackWebhookUrl;

    @Value("${dlq.alerts.slack.channel:#account-service-alerts}")
    private String slackChannel;

    @Value("${spring.application.name:account-service}")
    private String serviceName;

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void sendCriticalAlert(String title, String message, Map<String, String> context) {
        log.error("[CRITICAL ALERT] {} - {}", title, message);

        // Send to PagerDuty for immediate response
        if (pagerDutyEnabled) {
            sendPagerDutyAlert("critical", title, message, context);
        } else {
            log.warn("PagerDuty disabled - critical alert not sent to incident management");
        }

        // Also send to Slack for visibility
        if (slackEnabled) {
            sendSlackMessage(":rotating_light: **CRITICAL**", title, message, context, "#FF0000");
        } else {
            log.warn("Slack disabled - critical alert not sent to team channel");
        }
    }

    @Override
    public void sendHighPriorityAlert(String title, String message, Map<String, String> context) {
        log.warn("[HIGH PRIORITY ALERT] {} - {}", title, message);

        // High priority goes to Slack only (not PagerDuty)
        if (slackEnabled) {
            sendSlackMessage(":warning: **HIGH PRIORITY**", title, message, context, "#FFA500");
        } else {
            log.warn("Slack disabled - high priority alert not sent");
        }
    }

    @Override
    public void sendWarningAlert(String title, String message, Map<String, String> context) {
        log.warn("[WARNING] {} - {}", title, message);

        if (slackEnabled) {
            sendSlackMessage(":large_orange_diamond: **WARNING**", title, message, context, "#FFCC00");
        }
    }

    @Override
    public void sendInfoNotification(String title, String message, Map<String, String> context) {
        log.info("[INFO] {} - {}", title, message);

        if (slackEnabled) {
            sendSlackMessage(":information_source: **INFO**", title, message, context, "#0099FF");
        }
    }

    /**
     * Send alert to PagerDuty
     */
    private void sendPagerDutyAlert(
            String severity,
            String title,
            String message,
            Map<String, String> context) {

        if (!pagerDutyEnabled || pagerDutyIntegrationKey == null || pagerDutyIntegrationKey.isBlank()) {
            log.debug("PagerDuty not configured, skipping alert");
            return;
        }

        try {
            // Build PagerDuty Events API v2 payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("routing_key", pagerDutyIntegrationKey);
            payload.put("event_action", "trigger");
            payload.put("dedup_key", generateDedupKey(title, context));

            Map<String, Object> payloadDetails = new HashMap<>();
            payloadDetails.put("summary", title + " - " + message);
            payloadDetails.put("severity", severity);
            payloadDetails.put("source", serviceName);
            payloadDetails.put("timestamp", LocalDateTime.now().toString());

            // Add custom details
            Map<String, Object> customDetails = new HashMap<>(context);
            customDetails.put("service", serviceName);
            customDetails.put("message", message);
            customDetails.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMATTER));
            payloadDetails.put("custom_details", customDetails);

            payload.put("payload", payloadDetails);

            // Send to PagerDuty
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                pagerDutyApiUrl, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("PagerDuty alert sent successfully - title={}", title);
            } else {
                log.error("PagerDuty alert failed - status={}, title={}",
                    response.getStatusCode(), title);
            }

        } catch (Exception e) {
            log.error("Failed to send PagerDuty alert - title={}", title, e);
        }
    }

    /**
     * Send message to Slack
     */
    private void sendSlackMessage(
            String prefix,
            String title,
            String message,
            Map<String, String> context,
            String color) {

        if (!slackEnabled || slackWebhookUrl == null || slackWebhookUrl.isBlank()) {
            log.debug("Slack not configured, skipping message");
            return;
        }

        try {
            // Build Slack message with attachments
            Map<String, Object> payload = new HashMap<>();
            payload.put("channel", slackChannel);
            payload.put("username", serviceName + " DLQ Monitor");
            payload.put("icon_emoji", ":robot_face:");

            // Create attachment
            Map<String, Object> attachment = new HashMap<>();
            attachment.put("color", color);
            attachment.put("title", prefix + " " + title);
            attachment.put("text", message);
            attachment.put("ts", System.currentTimeMillis() / 1000);

            // Add context fields
            if (context != null && !context.isEmpty()) {
                var fields = context.entrySet().stream()
                    .map(entry -> Map.of(
                        "title", formatFieldName(entry.getKey()),
                        "value", entry.getValue(),
                        "short", true
                    ))
                    .toList();

                attachment.put("fields", fields);
            }

            // Add timestamp
            attachment.put("footer", serviceName);
            attachment.put("footer_icon", "https://platform.slack-edge.com/img/default_application_icon.png");

            payload.put("attachments", new Object[]{attachment});

            // Send to Slack
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                slackWebhookUrl, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("Slack message sent successfully - title={}", title);
            } else {
                log.error("Slack message failed - status={}, title={}",
                    response.getStatusCode(), title);
            }

        } catch (Exception e) {
            log.error("Failed to send Slack message - title={}", title, e);
        }
    }

    /**
     * Generate deduplication key for PagerDuty
     */
    private String generateDedupKey(String title, Map<String, String> context) {
        String correlationId = context != null ? context.get("correlationId") : null;
        if (correlationId != null) {
            return serviceName + ":" + title + ":" + correlationId;
        }
        return serviceName + ":" + title;
    }

    /**
     * Format field name for Slack (capitalize first letter)
     */
    private String formatFieldName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
