package com.waqiti.frauddetection.service.alerting;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Critical Alerting Service - PagerDuty and Slack Integration
 *
 * THIS WAS COMPLETELY MISSING - All critical failures were only logged, not escalated.
 * Now integrates with:
 * - PagerDuty for on-call engineer alerts
 * - Slack for team visibility
 * - Metrics for monitoring
 *
 * Alert Severity Levels:
 * - P0 (CRITICAL): Immediate page, financial/security risk
 * - P1 (HIGH): Alert within 15 minutes, operational impact
 * - P2 (MEDIUM): Alert within 1 hour, degraded functionality
 * - P3 (LOW): Next business day, informational
 *
 * @author Waqiti DevOps Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CriticalAlertingService {

    private final RestTemplate restTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${pagerduty.integration-key:${PAGERDUTY_INTEGRATION_KEY:}}")
    private String pagerDutyIntegrationKey;

    @Value("${slack.webhook-url:${SLACK_WEBHOOK_URL:}}")
    private String slackWebhookUrl;

    @Value("${alerting.enabled:true}")
    private boolean alertingEnabled;

    @Value("${spring.application.name:fraud-detection-service}")
    private String serviceName;

    private static final String PAGERDUTY_EVENTS_API = "https://events.pagerduty.com/v2/enqueue";

    /**
     * Raise P0 Critical Alert - Pages on-call immediately
     *
     * Use for:
     * - Transaction blocking failures
     * - Fraud detection service outages
     * - Financial data integrity issues
     * - Security breaches
     */
    @Async
    public void raiseP0Alert(String component, String message, Map<String, Object> context) {
        raiseAlert(AlertSeverity.CRITICAL, component, message, context);
    }

    /**
     * Raise P1 High Alert - Pages on-call within 15 minutes
     *
     * Use for:
     * - Service degradation
     * - High error rates
     * - Circuit breaker trips
     */
    @Async
    public void raiseP1Alert(String component, String message, Map<String, Object> context) {
        raiseAlert(AlertSeverity.HIGH, component, message, context);
    }

    /**
     * Raise P2 Medium Alert - Alert within 1 hour
     */
    @Async
    public void raiseP2Alert(String component, String message, Map<String, Object> context) {
        raiseAlert(AlertSeverity.MEDIUM, component, message, context);
    }

    /**
     * Raise P3 Low Alert - Next business day
     */
    @Async
    public void raiseP3Alert(String component, String message, Map<String, Object> context) {
        raiseAlert(AlertSeverity.LOW, component, message, context);
    }

    /**
     * Core alert raising method
     */
    private void raiseAlert(AlertSeverity severity, String component, String message, Map<String, Object> context) {
        if (!alertingEnabled) {
            log.warn("Alerting is disabled. Would have sent {} alert: {}", severity, message);
            return;
        }

        try {
            // Record metrics
            recordAlertMetric(severity, component);

            // Log alert
            log.error("[{}] Alert raised - Component: {}, Message: {}, Context: {}",
                     severity, component, message, context);

            // Send to PagerDuty (P0 and P1 only)
            if ((severity == AlertSeverity.CRITICAL || severity == AlertSeverity.HIGH)
                && isConfigured(pagerDutyIntegrationKey)) {
                sendPagerDutyAlert(severity, component, message, context);
            }

            // Send to Slack (all severities)
            if (isConfigured(slackWebhookUrl)) {
                sendSlackAlert(severity, component, message, context);
            }

        } catch (Exception e) {
            // Critical: If alerting itself fails, we MUST log it prominently
            log.error("CRITICAL: Alerting system failure. Original alert was: [{}] {} - {}. Alerting error: {}",
                     severity, component, message, e.getMessage(), e);

            // Try to at least send a Slack notification about the alerting failure
            tryEmergencySlackNotification(severity, component, message, e);
        }
    }

    /**
     * Send alert to PagerDuty Events API v2
     */
    private void sendPagerDutyAlert(AlertSeverity severity, String component, String message, Map<String, Object> context) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("routing_key", pagerDutyIntegrationKey);
            payload.put("event_action", "trigger");
            payload.put("dedup_key", generateDedupKey(component, message));

            Map<String, Object> payloadData = new HashMap<>();
            payloadData.put("summary", String.format("[%s] %s: %s", serviceName, component, message));
            payloadData.put("severity", mapSeverityToPagerDuty(severity));
            payloadData.put("source", serviceName);
            payloadData.put("component", component);
            payloadData.put("timestamp", LocalDateTime.now().toString());
            payloadData.put("custom_details", context);

            payload.put("payload", payloadData);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                PAGERDUTY_EVENTS_API,
                request,
                String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("PagerDuty alert sent successfully: {}", message);
            } else {
                log.error("PagerDuty alert failed with status {}: {}",
                         response.getStatusCode(), response.getBody());
            }

        } catch (Exception e) {
            log.error("Failed to send PagerDuty alert: {}", e.getMessage(), e);
        }
    }

    /**
     * Send alert to Slack webhook
     */
    private void sendSlackAlert(AlertSeverity severity, String component, String message, Map<String, Object> context) {
        try {
            String emoji = getEmojiForSeverity(severity);
            String color = getColorForSeverity(severity);
            String channel = getChannelForSeverity(severity);

            Map<String, Object> slackPayload = new HashMap<>();
            slackPayload.put("channel", channel);
            slackPayload.put("username", "Waqiti Alert Bot");
            slackPayload.put("icon_emoji", ":rotating_light:");

            Map<String, Object> attachment = new HashMap<>();
            attachment.put("color", color);
            attachment.put("title", String.format("%s %s Alert - %s", emoji, severity, component));
            attachment.put("text", message);
            attachment.put("footer", serviceName);
            attachment.put("ts", System.currentTimeMillis() / 1000);

            if (context != null && !context.isEmpty()) {
                StringBuilder contextText = new StringBuilder("*Context:*\n");
                context.forEach((key, value) ->
                    contextText.append("• ").append(key).append(": ").append(value).append("\n")
                );
                attachment.put("fields", new Object[]{
                    Map.of(
                        "title", "Additional Details",
                        "value", contextText.toString(),
                        "short", false
                    )
                });
            }

            slackPayload.put("attachments", new Object[]{attachment});

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(slackPayload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                slackWebhookUrl,
                request,
                String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Slack alert sent successfully to {}", channel);
            } else {
                log.error("Slack alert failed with status {}: {}",
                         response.getStatusCode(), response.getBody());
            }

        } catch (Exception e) {
            log.error("Failed to send Slack alert: {}", e.getMessage(), e);
        }
    }

    /**
     * Emergency fallback if alerting system itself fails
     */
    private void tryEmergencySlackNotification(AlertSeverity severity, String component, String message, Exception error) {
        try {
            if (isConfigured(slackWebhookUrl)) {
                Map<String, Object> emergencyPayload = Map.of(
                    "channel", "#critical-alerts",
                    "text", String.format(":warning: ALERTING SYSTEM FAILURE :warning:\n" +
                                         "Original Alert: [%s] %s - %s\n" +
                                         "Alerting Error: %s",
                                         severity, component, message, error.getMessage())
                );

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> request = new HttpEntity<>(emergencyPayload, headers);

                restTemplate.postForEntity(slackWebhookUrl, request, String.class);
            }
        } catch (Exception fallbackError) {
            log.error("Even emergency Slack notification failed: {}", fallbackError.getMessage());
        }
    }

    private void recordAlertMetric(AlertSeverity severity, String component) {
        Counter.builder("alerts.raised")
            .tag("severity", severity.name())
            .tag("component", component)
            .tag("service", serviceName)
            .register(meterRegistry)
            .increment();
    }

    private String generateDedupKey(String component, String message) {
        return String.format("%s-%s-%s",
            serviceName,
            component,
            message.hashCode()
        );
    }

    private String mapSeverityToPagerDuty(AlertSeverity severity) {
        return switch (severity) {
            case CRITICAL -> "critical";
            case HIGH -> "error";
            case MEDIUM -> "warning";
            case LOW -> "info";
        };
    }

    private String getEmojiForSeverity(AlertSeverity severity) {
        return switch (severity) {
            case CRITICAL -> "🚨";
            case HIGH -> "⚠️";
            case MEDIUM -> "ℹ️";
            case LOW -> "📋";
        };
    }

    private String getColorForSeverity(AlertSeverity severity) {
        return switch (severity) {
            case CRITICAL -> "danger";  // Red
            case HIGH -> "warning";     // Yellow
            case MEDIUM -> "#439FE0";   // Blue
            case LOW -> "good";         // Green
        };
    }

    private String getChannelForSeverity(AlertSeverity severity) {
        return switch (severity) {
            case CRITICAL, HIGH -> "#critical-alerts";
            case MEDIUM -> "#alerts";
            case LOW -> "#monitoring";
        };
    }

    private boolean isConfigured(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public enum AlertSeverity {
        CRITICAL,  // P0 - Immediate page
        HIGH,      // P1 - Alert within 15 minutes
        MEDIUM,    // P2 - Alert within 1 hour
        LOW        // P3 - Next business day
    }
}
