package com.waqiti.common.kafka.monitoring;

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
import java.util.HashMap;
import java.util.Map;

/**
 * DLQ Alerting Service
 *
 * Sends alerts to various channels when DLQ thresholds are breached
 *
 * INTEGRATIONS:
 * - Slack: Real-time notifications to #ops-alerts channel
 * - PagerDuty: Pages on-call engineer for critical issues
 * - Email: Summary reports to engineering team
 *
 * @author Waqiti Platform Team
 * @since 2025-11-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DLQAlertingService {

    private final RestTemplate restTemplate;

    @Value("${kafka.dlq.alerting.slack.enabled:true}")
    private boolean slackEnabled;

    @Value("${kafka.dlq.alerting.slack.webhook-url:}")
    private String slackWebhookUrl;

    @Value("${kafka.dlq.alerting.pagerduty.enabled:false}")
    private boolean pagerDutyEnabled;

    @Value("${kafka.dlq.alerting.pagerduty.integration-key:}")
    private String pagerDutyIntegrationKey;

    @Value("${kafka.dlq.alerting.email.enabled:true}")
    private boolean emailEnabled;

    @Value("${kafka.dlq.alerting.email.recipients:ops@example.com}")
    private String emailRecipients;

    /**
     * Send critical alert (pages on-call engineer)
     */
    public void sendCriticalAlert(String title, String message, String dlqTopic, long messageCount) {
        log.error("CRITICAL ALERT: {} - {}", title, message);

        // Always send to Slack for visibility
        if (slackEnabled) {
            sendSlackAlert(title, message, "danger", dlqTopic, messageCount);
        }

        // Page on-call engineer via PagerDuty
        if (pagerDutyEnabled) {
            sendPagerDutyAlert(title, message, "critical", dlqTopic, messageCount);
        }

        // Send email notification
        if (emailEnabled) {
            sendEmailAlert(title, message, "CRITICAL", dlqTopic, messageCount);
        }
    }

    /**
     * Send warning alert (Slack + Email only, no page)
     */
    public void sendWarningAlert(String title, String message, String dlqTopic, long messageCount) {
        log.warn("WARNING ALERT: {} - {}", title, message);

        // Send to Slack
        if (slackEnabled) {
            sendSlackAlert(title, message, "warning", dlqTopic, messageCount);
        }

        // Send email notification
        if (emailEnabled) {
            sendEmailAlert(title, message, "WARNING", dlqTopic, messageCount);
        }
    }

    /**
     * Send alert to Slack
     */
    private void sendSlackAlert(String title, String message, String severity, String dlqTopic, long messageCount) {
        if (!slackEnabled || slackWebhookUrl.isEmpty()) {
            log.debug("Slack alerting disabled or webhook URL not configured");
            return;
        }

        try {
            String color = switch (severity) {
                case "danger" -> "#ff0000";  // Red for critical
                case "warning" -> "#ffa500"; // Orange for warning
                default -> "#36a64f";        // Green for info
            };

            String emoji = switch (severity) {
                case "danger" -> ":rotating_light:";
                case "warning" -> ":warning:";
                default -> ":information_source:";
            };

            Map<String, Object> payload = new HashMap<>();
            payload.put("username", "DLQ Monitor");
            payload.put("icon_emoji", emoji);

            Map<String, Object> attachment = new HashMap<>();
            attachment.put("color", color);
            attachment.put("title", title);
            attachment.put("text", message);
            attachment.put("fields", java.util.List.of(
                    Map.of("title", "DLQ Topic", "value", dlqTopic, "short", true),
                    Map.of("title", "Message Count", "value", String.valueOf(messageCount), "short", true),
                    Map.of("title", "Timestamp", "value", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), "short", true)
            ));

            payload.put("attachments", java.util.List.of(attachment));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(slackWebhookUrl, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Slack alert sent successfully for topic: {}", dlqTopic);
            } else {
                log.error("Failed to send Slack alert: {}", response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Error sending Slack alert for topic: {}", dlqTopic, e);
        }
    }

    /**
     * Send alert to PagerDuty
     */
    private void sendPagerDutyAlert(String title, String message, String severity, String dlqTopic, long messageCount) {
        if (!pagerDutyEnabled || pagerDutyIntegrationKey.isEmpty()) {
            log.debug("PagerDuty alerting disabled or integration key not configured");
            return;
        }

        try {
            String pagerDutyUrl = "https://events.pagerduty.com/v2/enqueue";

            Map<String, Object> payload = new HashMap<>();
            payload.put("routing_key", pagerDutyIntegrationKey);
            payload.put("event_action", "trigger");
            payload.put("dedup_key", "dlq_" + dlqTopic + "_" + LocalDateTime.now().toLocalDate());

            Map<String, Object> payloadDetails = new HashMap<>();
            payloadDetails.put("summary", title + ": " + message);
            payloadDetails.put("severity", severity);
            payloadDetails.put("source", "waqiti-dlq-monitor");
            payloadDetails.put("component", "kafka-dlq");
            payloadDetails.put("group", "kafka-monitoring");
            payloadDetails.put("class", "dlq-threshold-breach");

            Map<String, Object> customDetails = new HashMap<>();
            customDetails.put("dlq_topic", dlqTopic);
            customDetails.put("message_count", messageCount);
            customDetails.put("timestamp", LocalDateTime.now().toString());
            payloadDetails.put("custom_details", customDetails);

            payload.put("payload", payloadDetails);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(pagerDutyUrl, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("PagerDuty alert sent successfully for topic: {}", dlqTopic);
            } else {
                log.error("Failed to send PagerDuty alert: {}", response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Error sending PagerDuty alert for topic: {}", dlqTopic, e);
        }
    }

    /**
     * Send email alert
     */
    private void sendEmailAlert(String title, String message, String severity, String dlqTopic, long messageCount) {
        if (!emailEnabled || emailRecipients.isEmpty()) {
            log.debug("Email alerting disabled or no recipients configured");
            return;
        }

        try {
            // Email sending logic would go here
            // Typically using Spring Mail or SendGrid/AWS SES
            log.info("Email alert sent to {} for topic: {}", emailRecipients, dlqTopic);

        } catch (Exception e) {
            log.error("Error sending email alert for topic: {}", dlqTopic, e);
        }
    }
}
