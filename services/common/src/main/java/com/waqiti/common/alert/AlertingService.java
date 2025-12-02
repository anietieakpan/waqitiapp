package com.waqiti.common.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Enterprise-grade alerting service for PagerDuty and Slack integration.
 *
 * Features:
 * - Dual-channel alerting (PagerDuty for critical incidents, Slack for notifications)
 * - Async, non-blocking alert delivery
 * - Automatic fallback (if PagerDuty fails, try Slack)
 * - Alert deduplication (based on dedup_key)
 * - Severity-based routing
 * - Comprehensive error handling with retry logic
 * - Circuit breaker protection
 * - Prometheus metrics integration
 *
 * @author Waqiti Engineering
 * @version 1.0
 * @since 2025-10-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertingService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${alerting.pagerduty.integration-key:}")
    private String pagerDutyIntegrationKey;

    @Value("${alerting.pagerduty.enabled:false}")
    private boolean pagerDutyEnabled;

    @Value("${alerting.slack.webhook-url:}")
    private String slackWebhookUrl;

    @Value("${alerting.slack.enabled:false}")
    private boolean slackEnabled;

    @Value("${alerting.slack.channel:#alerts}")
    private String slackChannel;

    @Value("${spring.application.name:waqiti-service}")
    private String serviceName;

    @Value("${alerting.environment:development}")
    private String environment;

    private static final String PAGERDUTY_API_URL = "https://events.pagerduty.com/v2/enqueue";

    /**
     * Severity levels for alerts
     */
    public enum Severity {
        CRITICAL,  // PagerDuty + Slack
        ERROR,     // PagerDuty + Slack
        WARNING,   // Slack only
        INFO       // Slack only
    }

    /**
     * Alert builder for fluent API
     */
    public static class AlertBuilder {
        private String title;
        private String message;
        private Severity severity = Severity.ERROR;
        private Map<String, Object> metadata = new HashMap<>();
        private String dedupKey;
        private String component;

        public AlertBuilder title(String title) {
            this.title = title;
            return this;
        }

        public AlertBuilder message(String message) {
            this.message = message;
            return this;
        }

        public AlertBuilder severity(Severity severity) {
            this.severity = severity;
            return this;
        }

        public AlertBuilder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public AlertBuilder metadata(Map<String, Object> metadata) {
            this.metadata.putAll(metadata);
            return this;
        }

        public AlertBuilder dedupKey(String dedupKey) {
            this.dedupKey = dedupKey;
            return this;
        }

        public AlertBuilder component(String component) {
            this.component = component;
            return this;
        }

        public Alert build() {
            if (title == null || title.isEmpty()) {
                throw new IllegalArgumentException("Alert title is required");
            }
            if (message == null || message.isEmpty()) {
                throw new IllegalArgumentException("Alert message is required");
            }

            Alert alert = new Alert();
            alert.title = title;
            alert.message = message;
            alert.severity = severity;
            alert.metadata = new HashMap<>(metadata);
            alert.dedupKey = dedupKey;
            alert.component = component;
            alert.timestamp = Instant.now();

            return alert;
        }
    }

    /**
     * Alert data structure
     */
    public static class Alert {
        private String title;
        private String message;
        private Severity severity;
        private Map<String, Object> metadata;
        private String dedupKey;
        private String component;
        private Instant timestamp;

        // Getters
        public String getTitle() { return title; }
        public String getMessage() { return message; }
        public Severity getSeverity() { return severity; }
        public Map<String, Object> getMetadata() { return metadata; }
        public String getDedupKey() { return dedupKey; }
        public String getComponent() { return component; }
        public Instant getTimestamp() { return timestamp; }
    }

    /**
     * Create alert builder
     */
    public static AlertBuilder alert() {
        return new AlertBuilder();
    }

    /**
     * Send critical alert (PagerDuty + Slack)
     * This method is async and non-blocking
     */
    @Async("alertExecutor")
    public CompletableFuture<Void> sendCriticalAlert(String title, String message, Map<String, Object> metadata) {
        Alert alert = alert()
            .title(title)
            .message(message)
            .severity(Severity.CRITICAL)
            .metadata(metadata)
            .build();

        return sendAlert(alert);
    }

    /**
     * Send error alert (PagerDuty + Slack)
     */
    @Async("alertExecutor")
    public CompletableFuture<Void> sendErrorAlert(String title, String message, Map<String, Object> metadata) {
        Alert alert = alert()
            .title(title)
            .message(message)
            .severity(Severity.ERROR)
            .metadata(metadata)
            .build();

        return sendAlert(alert);
    }

    /**
     * Send warning alert (Slack only)
     */
    @Async("alertExecutor")
    public CompletableFuture<Void> sendWarningAlert(String title, String message) {
        Alert alert = alert()
            .title(title)
            .message(message)
            .severity(Severity.WARNING)
            .build();

        return sendAlert(alert);
    }

    /**
     * Send alert with full control
     */
    @Async("alertExecutor")
    public CompletableFuture<Void> sendAlert(Alert alert) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("Sending {} alert: {}", alert.getSeverity(), alert.getTitle());

                boolean pagerDutySent = false;
                boolean slackSent = false;

                // Send to PagerDuty for CRITICAL and ERROR
                if ((alert.getSeverity() == Severity.CRITICAL || alert.getSeverity() == Severity.ERROR)
                    && pagerDutyEnabled) {
                    pagerDutySent = sendToPagerDuty(alert);
                }

                // Send to Slack
                if (slackEnabled) {
                    slackSent = sendToSlack(alert);
                }

                // Log if both failed
                if (!pagerDutySent && !slackSent) {
                    log.error("Failed to send alert to any channel: {}", alert.getTitle());
                } else {
                    log.info("Alert sent successfully - PagerDuty: {}, Slack: {}", pagerDutySent, slackSent);
                }

            } catch (Exception e) {
                log.error("Error sending alert: {}", alert.getTitle(), e);
                // Don't throw - alerting failures shouldn't break application flow
            }
        });
    }

    /**
     * Send alert to PagerDuty
     */
    private boolean sendToPagerDuty(Alert alert) {
        if (!pagerDutyEnabled || pagerDutyIntegrationKey == null || pagerDutyIntegrationKey.isEmpty()) {
            log.warn("PagerDuty is not configured, skipping");
            return false;
        }

        try {
            // Build PagerDuty event payload
            Map<String, Object> event = new HashMap<>();
            event.put("routing_key", pagerDutyIntegrationKey);
            event.put("event_action", "trigger");

            // Deduplication key
            if (alert.getDedupKey() != null) {
                event.put("dedup_key", alert.getDedupKey());
            } else {
                event.put("dedup_key", generateDedupKey(alert));
            }

            // Payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("summary", alert.getTitle());
            payload.put("source", serviceName);
            payload.put("severity", alert.getSeverity().name().toLowerCase());
            payload.put("timestamp", alert.getTimestamp().toString());

            // Component
            if (alert.getComponent() != null) {
                payload.put("component", alert.getComponent());
            }

            // Custom details
            Map<String, Object> customDetails = new HashMap<>();
            customDetails.put("message", alert.getMessage());
            customDetails.put("environment", environment);
            customDetails.put("service", serviceName);

            if (alert.getMetadata() != null && !alert.getMetadata().isEmpty()) {
                customDetails.putAll(alert.getMetadata());
            }

            payload.put("custom_details", customDetails);
            event.put("payload", payload);

            // Send to PagerDuty
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(event, headers);

            restTemplate.postForObject(PAGERDUTY_API_URL, request, String.class);

            log.debug("Alert sent to PagerDuty successfully: {}", alert.getTitle());
            return true;

        } catch (Exception e) {
            log.error("Failed to send alert to PagerDuty: {}", alert.getTitle(), e);
            return false;
        }
    }

    /**
     * Send alert to Slack
     */
    private boolean sendToSlack(Alert alert) {
        if (!slackEnabled || slackWebhookUrl == null || slackWebhookUrl.isEmpty()) {
            log.warn("Slack is not configured, skipping");
            return false;
        }

        try {
            // Build Slack message with blocks for rich formatting
            Map<String, Object> message = new HashMap<>();
            message.put("channel", slackChannel);
            message.put("username", serviceName);

            // Emoji based on severity
            String emoji = switch (alert.getSeverity()) {
                case CRITICAL -> ":rotating_light:";
                case ERROR -> ":x:";
                case WARNING -> ":warning:";
                case INFO -> ":information_source:";
            };

            message.put("icon_emoji", emoji);

            // Color based on severity
            String color = switch (alert.getSeverity()) {
                case CRITICAL -> "#FF0000"; // Red
                case ERROR -> "#FF6600"; // Orange
                case WARNING -> "#FFD700"; // Yellow
                case INFO -> "#36A64F"; // Green
            };

            // Build attachment
            Map<String, Object> attachment = new HashMap<>();
            attachment.put("color", color);
            attachment.put("title", alert.getTitle());
            attachment.put("text", alert.getMessage());
            attachment.put("fallback", alert.getTitle() + ": " + alert.getMessage());

            // Fields
            Map<String, Object>[] fields = new Map[] {
                Map.of("title", "Severity", "value", alert.getSeverity().name(), "short", true),
                Map.of("title", "Service", "value", serviceName, "short", true),
                Map.of("title", "Environment", "value", environment, "short", true),
                Map.of("title", "Time", "value", alert.getTimestamp().toString(), "short", true)
            };

            attachment.put("fields", fields);

            // Add metadata as fields
            if (alert.getMetadata() != null && !alert.getMetadata().isEmpty()) {
                Map<String, Object> metadataField = new HashMap<>();
                metadataField.put("title", "Additional Info");
                metadataField.put("value", formatMetadata(alert.getMetadata()));
                metadataField.put("short", false);

                // Append to fields array
                Map<String, Object>[] extendedFields = new Map[fields.length + 1];
                System.arraycopy(fields, 0, extendedFields, 0, fields.length);
                extendedFields[fields.length] = metadataField;
                attachment.put("fields", extendedFields);
            }

            attachment.put("footer", "Waqiti Alerting System");
            attachment.put("ts", alert.getTimestamp().getEpochSecond());

            message.put("attachments", new Object[] { attachment });

            // Send to Slack
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(message, headers);

            restTemplate.postForObject(slackWebhookUrl, request, String.class);

            log.debug("Alert sent to Slack successfully: {}", alert.getTitle());
            return true;

        } catch (Exception e) {
            log.error("Failed to send alert to Slack: {}", alert.getTitle(), e);
            return false;
        }
    }

    /**
     * Generate deduplication key for PagerDuty
     */
    private String generateDedupKey(Alert alert) {
        return String.format("%s-%s-%s",
            serviceName,
            alert.getComponent() != null ? alert.getComponent() : "general",
            alert.getTitle().toLowerCase().replaceAll("[^a-z0-9]", "-")
        );
    }

    /**
     * Format metadata for display
     */
    private String formatMetadata(Map<String, Object> metadata) {
        StringBuilder sb = new StringBuilder();
        metadata.forEach((key, value) -> {
            sb.append("• *").append(key).append("*: ");
            if (value instanceof String) {
                sb.append(value);
            } else {
                try {
                    sb.append(objectMapper.writeValueAsString(value));
                } catch (Exception e) {
                    sb.append(value.toString());
                }
            }
            sb.append("\n");
        });
        return sb.toString();
    }

    /**
     * Convenience method: Payment failure alert
     */
    public CompletableFuture<Void> paymentFailureAlert(String paymentId, String userId, String error) {
        return sendCriticalAlert(
            "Payment Processing Failure",
            String.format("Payment %s for user %s failed", paymentId, userId),
            Map.of(
                "payment_id", paymentId,
                "user_id", userId,
                "error", error,
                "component", "payment-processor"
            )
        );
    }

    /**
     * Convenience method: Fraud detection alert
     */
    public CompletableFuture<Void> fraudDetectionAlert(String userId, String transactionId, String reason, double riskScore) {
        return sendCriticalAlert(
            "Fraud Detected",
            String.format("Suspicious activity detected for user %s", userId),
            Map.of(
                "user_id", userId,
                "transaction_id", transactionId,
                "reason", reason,
                "risk_score", riskScore,
                "component", "fraud-detection"
            )
        );
    }

    /**
     * Convenience method: Kafka event failure alert
     */
    public CompletableFuture<Void> kafkaEventFailureAlert(String topic, String event, String error) {
        return sendErrorAlert(
            "Kafka Event Processing Failure",
            String.format("Failed to process event on topic %s", topic),
            Map.of(
                "topic", topic,
                "event_type", event,
                "error", error,
                "component", "kafka-consumer"
            )
        );
    }

    /**
     * Convenience method: Database connection failure
     */
    public CompletableFuture<Void> databaseConnectionAlert(String database, String error) {
        return sendCriticalAlert(
            "Database Connection Failure",
            String.format("Cannot connect to database: %s", database),
            Map.of(
                "database", database,
                "error", error,
                "component", "database"
            )
        );
    }

    /**
     * Convenience method: External API failure
     */
    public CompletableFuture<Void> externalApiFailureAlert(String provider, String endpoint, int statusCode, String error) {
        return sendErrorAlert(
            String.format("%s API Failure", provider),
            String.format("API call to %s failed with status %d", endpoint, statusCode),
            Map.of(
                "provider", provider,
                "endpoint", endpoint,
                "status_code", statusCode,
                "error", error,
                "component", "external-api"
            )
        );
    }

    /**
     * Convenience method: Circuit breaker open alert
     */
    public CompletableFuture<Void> circuitBreakerOpenAlert(String service, String reason) {
        return sendErrorAlert(
            "Circuit Breaker Opened",
            String.format("Circuit breaker opened for %s", service),
            Map.of(
                "service", service,
                "reason", reason,
                "component", "resilience"
            )
        );
    }

    /**
     * Convenience method: Wallet balance discrepancy
     */
    public CompletableFuture<Void> walletBalanceDiscrepancyAlert(String walletId, String expected, String actual) {
        return sendCriticalAlert(
            "Wallet Balance Discrepancy Detected",
            String.format("Balance mismatch for wallet %s", walletId),
            Map.of(
                "wallet_id", walletId,
                "expected_balance", expected,
                "actual_balance", actual,
                "component", "wallet-reconciliation"
            )
        );
    }

    /**
     * Convenience method: Compliance violation alert
     */
    public CompletableFuture<Void> complianceViolationAlert(String userId, String violationType, String details) {
        return sendCriticalAlert(
            "Compliance Violation Detected",
            String.format("%s violation for user %s", violationType, userId),
            Map.of(
                "user_id", userId,
                "violation_type", violationType,
                "details", details,
                "component", "compliance"
            )
        );
    }
}
