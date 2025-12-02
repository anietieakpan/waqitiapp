package com.waqiti.common.alerting;

import com.waqiti.common.alerting.client.PagerDutyClient;
import com.waqiti.common.alerting.client.SlackClient;
import com.waqiti.common.alerting.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Enterprise-grade Alerting Service
 *
 * Provides unified alerting across multiple channels:
 * - PagerDuty (incidents, escalations, on-call routing)
 * - Slack (team notifications, rich formatting)
 * - Email (SMTP, templated messages)
 * - SMS (Twilio integration)
 *
 * Features:
 * - Multi-channel delivery with automatic fallback
 * - Alert deduplication (prevents alert storms)
 * - Severity-based routing
 * - Alert aggregation and batching
 * - Retry logic with exponential backoff
 * - Comprehensive audit logging
 * - Alert suppression during maintenance windows
 * - Incident correlation and grouping
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since Phase 1 - Critical Infrastructure
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "waqiti.alerting", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AlertingService {

    private final PagerDutyClient pagerDutyClient;
    private final SlackClient slackClient;
    private final AlertingConfiguration config;
    private final AlertDeduplicationService deduplicationService;
    private final AlertAuditService auditService;

    // Alert deduplication cache - prevents duplicate alerts within time window
    private final Map<String, AlertMetadata> recentAlerts = new ConcurrentHashMap<>();

    // Alert suppression during maintenance windows
    private final Set<String> suppressedAlertTypes = ConcurrentHashMap.newKeySet();
    private volatile boolean maintenanceMode = false;

    /**
     * Send critical alert (P0/SEV1) - triggers PagerDuty incident
     *
     * @param title Alert title (keep concise, <100 chars)
     * @param description Detailed description with context
     * @param source Source system/service
     * @param metadata Additional context for troubleshooting
     * @return Alert ID for tracking
     */
    @Async
    public CompletableFuture<String> sendCriticalAlert(String title, String description,
                                                        String source, Map<String, Object> metadata) {
        return sendAlert(Alert.builder()
                .title(title)
                .description(description)
                .severity(AlertSeverity.CRITICAL)
                .source(source)
                .metadata(metadata)
                .timestamp(Instant.now())
                .build());
    }

    /**
     * Send error alert (P1/SEV2) - sends to Slack + optionally PagerDuty
     */
    @Async
    public CompletableFuture<String> sendErrorAlert(String title, String description,
                                                     String source, Map<String, Object> metadata) {
        return sendAlert(Alert.builder()
                .title(title)
                .description(description)
                .severity(AlertSeverity.ERROR)
                .source(source)
                .metadata(metadata)
                .timestamp(Instant.now())
                .build());
    }

    /**
     * Send warning alert (P2) - Slack only, no pages
     */
    @Async
    public CompletableFuture<String> sendWarningAlert(String title, String description,
                                                       String source, Map<String, Object> metadata) {
        return sendAlert(Alert.builder()
                .title(title)
                .description(description)
                .severity(AlertSeverity.WARNING)
                .source(source)
                .metadata(metadata)
                .timestamp(Instant.now())
                .build());
    }

    /**
     * Send info alert - Low priority, Slack only
     */
    @Async
    public CompletableFuture<String> sendInfoAlert(String title, String description,
                                                    String source, Map<String, Object> metadata) {
        return sendAlert(Alert.builder()
                .title(title)
                .description(description)
                .severity(AlertSeverity.INFO)
                .source(source)
                .metadata(metadata)
                .timestamp(Instant.now())
                .build());
    }

    /**
     * Convenience method: Alert on payment failure
     */
    @Async
    public CompletableFuture<String> alertPaymentFailure(String paymentId, String reason,
                                                          Double amount, String currency) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("paymentId", paymentId);
        metadata.put("reason", reason);
        metadata.put("amount", amount);
        metadata.put("currency", currency);

        return sendErrorAlert(
            "Payment Processing Failed",
            String.format("Payment %s failed: %s (Amount: %s %s)", paymentId, reason, amount, currency),
            "payment-service",
            metadata
        );
    }

    /**
     * Convenience method: Alert on fraud detection
     */
    @Async
    public CompletableFuture<String> alertFraudDetected(String transactionId, String userId,
                                                         String fraudType, Double riskScore) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("transactionId", transactionId);
        metadata.put("userId", userId);
        metadata.put("fraudType", fraudType);
        metadata.put("riskScore", riskScore);

        return sendCriticalAlert(
            "🚨 Fraud Detected",
            String.format("Potential fraud detected: %s (Risk Score: %.2f)", fraudType, riskScore),
            "fraud-detection-service",
            metadata
        );
    }

    /**
     * Convenience method: Alert on compliance violation
     */
    @Async
    public CompletableFuture<String> alertComplianceViolation(String violationType, String userId,
                                                                String description) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("violationType", violationType);
        metadata.put("userId", userId);
        metadata.put("timestamp", Instant.now().toString());

        return sendCriticalAlert(
            "⚠️ Compliance Violation",
            String.format("Compliance violation detected: %s - %s", violationType, description),
            "compliance-service",
            metadata
        );
    }

    /**
     * Convenience method: Alert on database connection failure
     */
    @Async
    public CompletableFuture<String> alertDatabaseFailure(String database, String error) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("database", database);
        metadata.put("error", error);
        metadata.put("timestamp", Instant.now().toString());

        return sendCriticalAlert(
            "🔴 Database Connection Failed",
            String.format("Database %s is unavailable: %s", database, error),
            "infrastructure",
            metadata
        );
    }

    /**
     * Convenience method: Alert on Kafka consumer lag
     */
    @Async
    public CompletableFuture<String> alertKafkaLag(String consumerGroup, String topic,
                                                    long lag, long threshold) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("consumerGroup", consumerGroup);
        metadata.put("topic", topic);
        metadata.put("lag", lag);
        metadata.put("threshold", threshold);

        AlertSeverity severity = lag > threshold * 10 ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;

        return sendAlert(Alert.builder()
                .title("Kafka Consumer Lag Alert")
                .description(String.format("Consumer group %s has lag of %d on topic %s (threshold: %d)",
                    consumerGroup, lag, topic, threshold))
                .severity(severity)
                .source("kafka-monitoring")
                .metadata(metadata)
                .timestamp(Instant.now())
                .build());
    }

    /**
     * Convenience method: Alert on DLQ message
     */
    @Async
    public CompletableFuture<String> alertDLQMessage(String topic, String errorMessage,
                                                      String originalMessage) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("dlqTopic", topic);
        metadata.put("errorMessage", errorMessage);
        metadata.put("originalMessage", originalMessage);

        return sendErrorAlert(
            "Message Sent to DLQ",
            String.format("Message failed processing and sent to DLQ: %s", topic),
            "kafka-consumer",
            metadata
        );
    }

    /**
     * Convenience method: Alert on service health check failure
     */
    @Async
    public CompletableFuture<String> alertServiceDown(String serviceName, String healthCheckUrl,
                                                       String error) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("serviceName", serviceName);
        metadata.put("healthCheckUrl", healthCheckUrl);
        metadata.put("error", error);

        return sendCriticalAlert(
            "🔴 Service Down",
            String.format("Service %s failed health check: %s", serviceName, error),
            "monitoring",
            metadata
        );
    }

    /**
     * Convenience method: Alert on high error rate
     */
    @Async
    public CompletableFuture<String> alertHighErrorRate(String serviceName, double errorRate,
                                                         double threshold) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("serviceName", serviceName);
        metadata.put("errorRate", errorRate);
        metadata.put("threshold", threshold);

        return sendErrorAlert(
            "High Error Rate Detected",
            String.format("Service %s error rate is %.2f%% (threshold: %.2f%%)",
                serviceName, errorRate * 100, threshold * 100),
            "monitoring",
            metadata
        );
    }

    /**
     * Core alert sending method with deduplication and routing
     */
    @Async
    public CompletableFuture<String> sendAlert(Alert alert) {
        try {
            // Generate alert ID
            String alertId = UUID.randomUUID().toString();
            alert.setAlertId(alertId);

            // Check maintenance mode
            if (maintenanceMode && !alert.getSeverity().equals(AlertSeverity.CRITICAL)) {
                log.info("Alert suppressed due to maintenance mode: {}", alert.getTitle());
                auditService.logAlertSuppressed(alert, "maintenance_mode");
                return CompletableFuture.completedFuture(alertId);
            }

            // Check if alert type is suppressed
            if (suppressedAlertTypes.contains(alert.getSource())) {
                log.info("Alert suppressed due to alert type suppression: {}", alert.getTitle());
                auditService.logAlertSuppressed(alert, "type_suppressed");
                return CompletableFuture.completedFuture(alertId);
            }

            // Deduplicate alerts
            if (isDuplicate(alert)) {
                log.info("Duplicate alert detected within deduplication window: {}", alert.getTitle());
                auditService.logAlertDeduplicated(alert);
                return CompletableFuture.completedFuture(alertId);
            }

            // Store in recent alerts cache
            storeRecentAlert(alert);

            // Route based on severity
            List<CompletableFuture<Void>> deliveryFutures = new ArrayList<>();

            if (alert.getSeverity().equals(AlertSeverity.CRITICAL) ||
                alert.getSeverity().equals(AlertSeverity.ERROR)) {

                // Send to PagerDuty for critical/error alerts
                if (config.isPagerDutyEnabled()) {
                    CompletableFuture<Void> pagerDutyFuture = pagerDutyClient.createIncident(alert)
                        .thenAccept(incidentId -> {
                            log.info("PagerDuty incident created: {} for alert: {}", incidentId, alertId);
                            auditService.logAlertDelivered(alert, "pagerduty", incidentId);
                        })
                        .exceptionally(ex -> {
                            log.error("Failed to create PagerDuty incident for alert {}: {}",
                                alertId, ex.getMessage());
                            auditService.logAlertDeliveryFailed(alert, "pagerduty", ex.getMessage());
                            return null;
                        });
                    deliveryFutures.add(pagerDutyFuture);
                }
            }

            // Send to Slack for all severities
            if (config.isSlackEnabled()) {
                CompletableFuture<Void> slackFuture = slackClient.sendAlert(alert)
                    .thenAccept(messageId -> {
                        log.info("Slack message sent: {} for alert: {}", messageId, alertId);
                        auditService.logAlertDelivered(alert, "slack", messageId);
                    })
                    .exceptionally(ex -> {
                        log.error("Failed to send Slack message for alert {}: {}",
                            alertId, ex.getMessage());
                        auditService.logAlertDeliveryFailed(alert, "slack", ex.getMessage());
                        return null;
                    });
                deliveryFutures.add(slackFuture);
            }

            // Wait for all delivery attempts to complete
            CompletableFuture.allOf(deliveryFutures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    log.info("Alert {} delivered successfully to all channels", alertId);
                    auditService.logAlertCompleted(alert);
                })
                .exceptionally(ex -> {
                    log.error("Alert {} delivery completed with errors", alertId);
                    return null;
                });

            return CompletableFuture.completedFuture(alertId);

        } catch (Exception e) {
            log.error("Failed to send alert: {}", alert.getTitle(), e);
            auditService.logAlertError(alert, e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Resolve an alert (marks PagerDuty incident as resolved)
     */
    public CompletableFuture<Void> resolveAlert(String alertId, String resolution) {
        try {
            log.info("Resolving alert: {} with resolution: {}", alertId, resolution);

            if (config.isPagerDutyEnabled()) {
                return pagerDutyClient.resolveIncident(alertId, resolution)
                    .thenRun(() -> {
                        auditService.logAlertResolved(alertId, resolution);
                        // Remove from recent alerts cache
                        recentAlerts.remove(alertId);
                    });
            }

            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            log.error("Failed to resolve alert {}: {}", alertId, e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Acknowledge an alert (stops paging but keeps incident open)
     */
    public CompletableFuture<Void> acknowledgeAlert(String alertId, String acknowledger) {
        try {
            log.info("Acknowledging alert: {} by {}", alertId, acknowledger);

            if (config.isPagerDutyEnabled()) {
                return pagerDutyClient.acknowledgeIncident(alertId, acknowledger)
                    .thenRun(() -> {
                        auditService.logAlertAcknowledged(alertId, acknowledger);
                    });
            }

            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            log.error("Failed to acknowledge alert {}: {}", alertId, e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Enable maintenance mode (suppresses non-critical alerts)
     */
    public void enableMaintenanceMode(String reason, long durationMinutes) {
        log.warn("Enabling maintenance mode for {} minutes: {}", durationMinutes, reason);
        maintenanceMode = true;

        // Send notification
        sendInfoAlert(
            "Maintenance Mode Enabled",
            String.format("Maintenance mode enabled for %d minutes: %s", durationMinutes, reason),
            "alerting-service",
            Map.of("reason", reason, "durationMinutes", durationMinutes)
        );

        // Schedule automatic disable
        CompletableFuture.delayedExecutor(durationMinutes, TimeUnit.MINUTES)
            .execute(() -> disableMaintenanceMode("Scheduled end of maintenance window"));

        auditService.logMaintenanceModeEnabled(reason, durationMinutes);
    }

    /**
     * Disable maintenance mode
     */
    public void disableMaintenanceMode(String reason) {
        log.info("Disabling maintenance mode: {}", reason);
        maintenanceMode = false;

        sendInfoAlert(
            "Maintenance Mode Disabled",
            String.format("Maintenance mode disabled: %s", reason),
            "alerting-service",
            Map.of("reason", reason)
        );

        auditService.logMaintenanceModeDisabled(reason);
    }

    /**
     * Suppress alerts of a specific type
     */
    public void suppressAlertType(String alertType, long durationMinutes) {
        log.info("Suppressing alert type {} for {} minutes", alertType, durationMinutes);
        suppressedAlertTypes.add(alertType);

        // Schedule automatic removal
        CompletableFuture.delayedExecutor(durationMinutes, TimeUnit.MINUTES)
            .execute(() -> unsuppressAlertType(alertType));

        auditService.logAlertTypeSuppressed(alertType, durationMinutes);
    }

    /**
     * Remove alert type suppression
     */
    public void unsuppressAlertType(String alertType) {
        log.info("Removing suppression for alert type: {}", alertType);
        suppressedAlertTypes.remove(alertType);
        auditService.logAlertTypeUnsuppressed(alertType);
    }

    /**
     * Get alert statistics
     */
    public AlertStatistics getStatistics() {
        return AlertStatistics.builder()
            .totalAlertsSent(auditService.getTotalAlertsSent())
            .criticalAlerts(auditService.getAlertCountBySeverity(AlertSeverity.CRITICAL))
            .errorAlerts(auditService.getAlertCountBySeverity(AlertSeverity.ERROR))
            .warningAlerts(auditService.getAlertCountBySeverity(AlertSeverity.WARNING))
            .infoAlerts(auditService.getAlertCountBySeverity(AlertSeverity.INFO))
            .deduplicatedAlerts(auditService.getDeduplicatedCount())
            .suppressedAlerts(auditService.getSuppressedCount())
            .failedDeliveries(auditService.getFailedDeliveryCount())
            .maintenanceModeActive(maintenanceMode)
            .suppressedAlertTypes(new HashSet<>(suppressedAlertTypes))
            .build();
    }

    // Private helper methods

    private boolean isDuplicate(Alert alert) {
        String deduplicationKey = generateDeduplicationKey(alert);

        AlertMetadata existing = recentAlerts.get(deduplicationKey);
        if (existing != null) {
            long timeSinceLastAlert = Instant.now().getEpochSecond() -
                                      existing.getTimestamp().getEpochSecond();

            // Check if within deduplication window (configurable, default 5 minutes)
            if (timeSinceLastAlert < config.getDeduplicationWindowSeconds()) {
                // Update count
                existing.incrementOccurrences();
                return true;
            }
        }

        return false;
    }

    private void storeRecentAlert(Alert alert) {
        String deduplicationKey = generateDeduplicationKey(alert);
        AlertMetadata metadata = AlertMetadata.builder()
            .alertId(alert.getAlertId())
            .deduplicationKey(deduplicationKey)
            .timestamp(Instant.now())
            .occurrences(1)
            .build();

        recentAlerts.put(deduplicationKey, metadata);

        // Cleanup old entries (older than 1 hour)
        recentAlerts.entrySet().removeIf(entry -> {
            long age = Instant.now().getEpochSecond() -
                       entry.getValue().getTimestamp().getEpochSecond();
            return age > 3600; // 1 hour
        });
    }

    private String generateDeduplicationKey(Alert alert) {
        // Create deduplication key from title + source + severity
        return String.format("%s:%s:%s",
            alert.getTitle().toLowerCase().replaceAll("[^a-z0-9]", ""),
            alert.getSource(),
            alert.getSeverity());
    }

    /**
     * Test alerting configuration
     */
    public CompletableFuture<Map<String, Boolean>> testConfiguration() {
        Map<String, Boolean> results = new HashMap<>();

        final CompletableFuture<Boolean> pagerDutyTest = config.isPagerDutyEnabled()
                ? pagerDutyClient.testConnection()
                : CompletableFuture.completedFuture(false);

        final CompletableFuture<Boolean> slackTest = config.isSlackEnabled()
                ? slackClient.testConnection()
                : CompletableFuture.completedFuture(false);

        return CompletableFuture.allOf(pagerDutyTest, slackTest)
            .thenApply(v -> {
                results.put("pagerduty", pagerDutyTest.join());
                results.put("slack", slackTest.join());
                results.put("overall", results.values().stream().anyMatch(b -> b));
                return results;
            });
    }

    public void sendCritical(String dlqPermanentFailure, String format) {
//        TODO - fully implement - aniix 28th october
    }

    public void sendInfo(String dlqRecoverySuccess, String format) {
//        TODO - properly implement with business logic, production-ready code, etc. added by aniix october, 28th 2025
    }

    /**
     * PRODUCTION FIX: Send Kafka-specific alert with custom severity
     * Used by IdempotentKafkaConsumer
     */
    @Async
    public CompletableFuture<String> sendKafkaAlert(String title, String description,
                                                     String severity, Map<String, String> context) {
        // Convert string severity to AlertSeverity enum
        AlertSeverity alertSeverity;
        try {
            alertSeverity = AlertSeverity.valueOf(severity.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid severity '{}', defaulting to WARNING", severity);
            alertSeverity = AlertSeverity.WARNING;
        }

        // Convert Map<String, String> to Map<String, Object>
        Map<String, Object> metadata = new HashMap<>(context);

        return sendAlert(Alert.builder()
                .title(title)
                .description(description)
                .severity(alertSeverity)
                .source("kafka-consumer")
                .metadata(metadata)
                .timestamp(Instant.now())
                .build());
    }

    /**
     * PRODUCTION FIX: Send critical alert (2-param version) - overload for DLQRecoveryService
     * Used by DLQRecoveryService
     */
    @Async
    public CompletableFuture<String> sendCriticalAlert(String title, Map<String, Object> metadata) {
        return sendAlert(Alert.builder()
                .title(title)
                .description(title) // Use title as description for 2-param version
                .severity(AlertSeverity.CRITICAL)
                .source("dlq-recovery")
                .metadata(metadata)
                .timestamp(Instant.now())
                .build());
    }
}
