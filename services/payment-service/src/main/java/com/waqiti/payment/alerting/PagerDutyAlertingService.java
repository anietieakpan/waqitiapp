package com.waqiti.payment.alerting;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * PagerDuty Alerting Service
 *
 * Centralized service for triggering PagerDuty alerts for critical payment system failures.
 * Supports both Events API v2 for alerting and Slack integration for team notifications.
 *
 * Features:
 * - PagerDuty Events API v2 integration
 * - Slack webhook integration
 * - Alert deduplication
 * - Severity-based routing
 * - Comprehensive error handling
 * - Async processing for performance
 * - Metrics tracking
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PagerDutyAlertingService {

    private final RestTemplate restTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${pagerduty.api-key}")
    private String pagerDutyApiKey;

    @Value("${pagerduty.integration-key}")
    private String pagerDutyIntegrationKey;

    @Value("${pagerduty.events-url:https://events.pagerduty.com/v2/enqueue}")
    private String pagerDutyEventsUrl;

    @Value("${slack.webhook-url:}")
    private String slackWebhookUrl;

    @Value("${alerting.enabled:true}")
    private boolean alertingEnabled;

    @Value("${alerting.slack-enabled:true}")
    private boolean slackEnabled;

    private final Counter pagerDutyAlertsCounter;
    private final Counter slackAlertsCounter;
    private final Counter failedAlertsCounter;

    public PagerDutyAlertingService(RestTemplate restTemplate, MeterRegistry meterRegistry) {
        this.restTemplate = restTemplate;
        this.meterRegistry = meterRegistry;

        // Initialize metrics
        this.pagerDutyAlertsCounter = Counter.builder("alerting.pagerduty.sent")
                .description("Number of PagerDuty alerts sent")
                .register(meterRegistry);

        this.slackAlertsCounter = Counter.builder("alerting.slack.sent")
                .description("Number of Slack alerts sent")
                .register(meterRegistry);

        this.failedAlertsCounter = Counter.builder("alerting.failed")
                .description("Number of failed alert attempts")
                .register(meterRegistry);
    }

    /**
     * Trigger critical alert for payment processing failures
     */
    @Async
    public CompletableFuture<Void> triggerCriticalAlert(
            String title,
            String description,
            Map<String, Object> customDetails) {

        return triggerAlert(AlertSeverity.CRITICAL, title, description, customDetails);
    }

    /**
     * Trigger error alert for non-critical issues
     */
    @Async
    public CompletableFuture<Void> triggerErrorAlert(
            String title,
            String description,
            Map<String, Object> customDetails) {

        return triggerAlert(AlertSeverity.ERROR, title, description, customDetails);
    }

    /**
     * Trigger warning alert for potential issues
     */
    @Async
    public CompletableFuture<Void> triggerWarningAlert(
            String title,
            String description,
            Map<String, Object> customDetails) {

        return triggerAlert(AlertSeverity.WARNING, title, description, customDetails);
    }

    /**
     * Trigger alert with specified severity
     */
    @Async
    public CompletableFuture<Void> triggerAlert(
            AlertSeverity severity,
            String title,
            String description,
            Map<String, Object> customDetails) {

        if (!alertingEnabled) {
            log.debug("Alerting is disabled, skipping alert: {}", title);
            return CompletableFuture.completedFuture(null);
        }

        try {
            log.info("Triggering {} alert: {}", severity, title);

            // Send to PagerDuty
            if (severity == AlertSeverity.CRITICAL || severity == AlertSeverity.ERROR) {
                sendPagerDutyAlert(severity, title, description, customDetails);
            }

            // Send to Slack for all severities
            if (slackEnabled && slackWebhookUrl != null && !slackWebhookUrl.isEmpty()) {
                sendSlackNotification(severity, title, description, customDetails);
            }

            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            log.error("Failed to trigger alert: {}", title, e);
            failedAlertsCounter.increment();
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Send alert to PagerDuty Events API v2
     */
    private void sendPagerDutyAlert(
            AlertSeverity severity,
            String title,
            String description,
            Map<String, Object> customDetails) {

        try {
            // Create PagerDuty event payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("routing_key", pagerDutyIntegrationKey);
            payload.put("event_action", "trigger");
            payload.put("dedup_key", generateDedupKey(title));

            // Event payload
            Map<String, Object> payloadData = new HashMap<>();
            payloadData.put("summary", title);
            payloadData.put("source", "payment-service");
            payloadData.put("severity", mapSeverityToPagerDuty(severity));
            payloadData.put("timestamp", ZonedDateTime.now().toString());
            payloadData.put("component", "payment-processing");
            payloadData.put("class", severity.name());

            // Custom details
            Map<String, Object> details = new HashMap<>(customDetails != null ? customDetails : Map.of());
            details.put("description", description);
            details.put("environment", System.getProperty("spring.profiles.active", "production"));
            details.put("service", "payment-service");
            payloadData.put("custom_details", details);

            payload.put("payload", payloadData);

            // Send to PagerDuty
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Token token=" + pagerDutyApiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    pagerDutyEventsUrl,
                    request,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.ACCEPTED) {
                log.info("Successfully sent PagerDuty alert: {}", title);
                pagerDutyAlertsCounter.increment();
            } else {
                log.warn("PagerDuty alert returned status: {}", response.getStatusCode());
                failedAlertsCounter.increment();
            }

        } catch (Exception e) {
            log.error("Failed to send PagerDuty alert: {}", title, e);
            failedAlertsCounter.increment();
        }
    }

    /**
     * Send notification to Slack
     */
    private void sendSlackNotification(
            AlertSeverity severity,
            String title,
            String description,
            Map<String, Object> customDetails) {

        try {
            // Create Slack message payload
            Map<String, Object> payload = new HashMap<>();

            // Format message with severity emoji
            String emoji = getSeverityEmoji(severity);
            String severityText = severity.name();

            String text = String.format("%s *[%s]* %s\n\n%s",
                    emoji, severityText, title, description);

            payload.put("text", text);
            payload.put("username", "Payment Service Alert");
            payload.put("icon_emoji", ":rotating_light:");

            // Add custom details as attachment
            if (customDetails != null && !customDetails.isEmpty()) {
                Map<String, Object> attachment = new HashMap<>();
                attachment.put("color", getSeverityColor(severity));
                attachment.put("fields", formatDetailsForSlack(customDetails));
                attachment.put("footer", "Payment Service");
                attachment.put("ts", Instant.now().getEpochSecond());

                payload.put("attachments", new Object[]{attachment});
            }

            // Send to Slack
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    slackWebhookUrl,
                    request,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("Successfully sent Slack notification: {}", title);
                slackAlertsCounter.increment();
            } else {
                log.warn("Slack notification returned status: {}", response.getStatusCode());
                failedAlertsCounter.increment();
            }

        } catch (Exception e) {
            log.error("Failed to send Slack notification: {}", title, e);
            failedAlertsCounter.increment();
        }
    }

    /**
     * Generate deduplication key for PagerDuty
     */
    private String generateDedupKey(String title) {
        // Use title hash for deduplication - same issue won't create multiple alerts
        return "payment-service-" + Integer.toHexString(title.hashCode());
    }

    /**
     * Map internal severity to PagerDuty severity
     */
    private String mapSeverityToPagerDuty(AlertSeverity severity) {
        return switch (severity) {
            case CRITICAL -> "critical";
            case ERROR -> "error";
            case WARNING -> "warning";
            case INFO -> "info";
        };
    }

    /**
     * Get emoji for severity
     */
    private String getSeverityEmoji(AlertSeverity severity) {
        return switch (severity) {
            case CRITICAL -> ":fire:";
            case ERROR -> ":x:";
            case WARNING -> ":warning:";
            case INFO -> ":information_source:";
        };
    }

    /**
     * Get Slack color for severity
     */
    private String getSeverityColor(AlertSeverity severity) {
        return switch (severity) {
            case CRITICAL -> "#ff0000"; // Red
            case ERROR -> "#ff6600"; // Orange
            case WARNING -> "#ffcc00"; // Yellow
            case INFO -> "#0099ff"; // Blue
        };
    }

    /**
     * Format custom details for Slack attachment
     */
    private Object[] formatDetailsForSlack(Map<String, Object> details) {
        return details.entrySet().stream()
                .map(entry -> Map.of(
                        "title", formatFieldName(entry.getKey()),
                        "value", String.valueOf(entry.getValue()),
                        "short", entry.getValue().toString().length() < 40
                ))
                .toArray();
    }

    /**
     * Format field name for display
     */
    private String formatFieldName(String fieldName) {
        return fieldName.replace("_", " ")
                .replace("-", " ")
                .substring(0, 1).toUpperCase() +
                fieldName.replace("_", " ")
                        .replace("-", " ")
                        .substring(1);
    }

    /**
     * Alert severity levels
     */
    public enum AlertSeverity {
        CRITICAL,
        ERROR,
        WARNING,
        INFO
    }

    /**
     * Convenience methods for common alert scenarios
     */

    @Async
    public CompletableFuture<Void> alertDLQFailure(String topic, int partition, long offset, String error) {
        return triggerCriticalAlert(
                "Dead Letter Queue Handling Failed",
                "Critical failure in DLQ processing - message may be lost",
                Map.of(
                        "topic", topic,
                        "partition", partition,
                        "offset", offset,
                        "error", error,
                        "action_required", "Manual intervention needed"
                )
        );
    }

    @Async
    public CompletableFuture<Void> alertPaymentProviderFailure(String provider, String errorCode, String errorMessage) {
        return triggerCriticalAlert(
                String.format("Payment Provider Failure: %s", provider),
                "Payment provider is experiencing issues",
                Map.of(
                        "provider", provider,
                        "error_code", errorCode,
                        "error_message", errorMessage,
                        "fallback_status", "Check fallback service logs"
                )
        );
    }

    @Async
    public CompletableFuture<Void> alertRefundDiscrepancy(String refundId, String description, String severity) {
        return triggerErrorAlert(
                String.format("Refund Reconciliation Discrepancy: %s", refundId),
                description,
                Map.of(
                        "refund_id", refundId,
                        "discrepancy_severity", severity,
                        "action_required", "Review refund reconciliation"
                )
        );
    }

    @Async
    public CompletableFuture<Void> alertOfflinePaymentSyncFailure(String paymentId, int attempts, String error) {
        return triggerErrorAlert(
                "Offline Payment Sync Permanent Failure",
                String.format("Offline payment failed to sync after %d attempts", attempts),
                Map.of(
                        "payment_id", paymentId,
                        "sync_attempts", attempts,
                        "error", error,
                        "action_required", "Manual sync required"
                )
        );
    }

    @Async
    public CompletableFuture<Void> alertACHBatchProcessingFailure(String batchId, int transactionCount, String error) {
        return triggerCriticalAlert(
                "ACH Batch Processing Failure",
                String.format("ACH batch with %d transactions failed processing", transactionCount),
                Map.of(
                        "batch_id", batchId,
                        "transaction_count", transactionCount,
                        "error", error,
                        "action_required", "Review batch and reprocess"
                )
        );
    }

    @Async
    public CompletableFuture<Void> alertComplianceViolation(String userId, String violationType, String details) {
        return triggerCriticalAlert(
                String.format("Compliance Violation: %s", violationType),
                "Critical compliance issue detected",
                Map.of(
                        "user_id", userId,
                        "violation_type", violationType,
                        "details", details,
                        "action_required", "Immediate compliance review required"
                )
        );
    }

    @Async
    public CompletableFuture<Void> alertCircuitBreakerOpen(String serviceName, String circuitBreakerName) {
        return triggerErrorAlert(
                String.format("Circuit Breaker Opened: %s", circuitBreakerName),
                String.format("Circuit breaker for %s is now open - service degraded", serviceName),
                Map.of(
                        "service", serviceName,
                        "circuit_breaker", circuitBreakerName,
                        "impact", "Some payment operations may be failing",
                        "action_required", "Check service health and logs"
                )
        );
    }
}
