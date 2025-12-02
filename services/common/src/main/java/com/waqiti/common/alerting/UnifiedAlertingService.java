package com.waqiti.common.alerting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Unified Alerting Service - Single Entry Point for All Alerts
 *
 * Provides a unified interface for sending alerts to multiple channels:
 * - PagerDuty (for on-call escalation and incident management)
 * - Slack (for team notifications and collaboration)
 *
 * Features:
 * - Parallel alert delivery to multiple channels
 * - Automatic channel selection based on severity
 * - Comprehensive error handling and fallback
 * - Async execution for performance
 * - Deduplication support
 *
 * Usage Examples:
 *
 * // Critical production incident
 * alertService.sendCriticalAlert(
 *     "Payment Processing Down",
 *     "Payment service experiencing 100% error rate",
 *     Map.of(
 *         "error_rate", "100%",
 *         "affected_users", 1500,
 *         "dashboard_url", "https://api.example.com/d/payments"
 *     )
 * );
 *
 * // High-risk fraud detection
 * alertService.sendFraudAlert(
 *     "High-Risk Transaction Detected",
 *     "Transaction $50,000 flagged with fraud score 95/100",
 *     Map.of(
 *         "transaction_id", "txn_123",
 *         "user_id", "user_456",
 *         "fraud_score", 95,
 *         "amount", 50000
 *     )
 * );
 *
 * // Warning for monitoring
 * alertService.sendWarningAlert(
 *     "High API Latency",
 *     "P95 latency increased to 2.5s",
 *     Map.of("service", "wallet-service", "p95_latency_ms", 2500)
 * );
 *
 * @author Waqiti Platform Engineering Team
 * @version 2.0.0
 * @since 2025-10-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnifiedAlertingService {

    private final PagerDutyAlertService pagerDutyService;
    private final SlackAlertService slackService;

    /**
     * Send CRITICAL alert to all channels (PagerDuty + Slack)
     * Pages on-call engineer immediately
     *
     * Use for:
     * - Production outages
     * - Data loss incidents
     * - Security breaches
     * - Payment processing failures
     * - Compliance violations
     */
    public AlertResult sendCriticalAlert(String title, String description, Map<String, Object> details) {
        log.error("🚨 CRITICAL ALERT: {}", title);

        String dedupKey = generateDedupKey(title, "critical");

        // Send to both PagerDuty and Slack in parallel
        CompletableFuture<String> pagerDutyFuture = CompletableFuture.supplyAsync(() ->
            pagerDutyService.sendCriticalAlert(title, description, dedupKey, details)
        );

        CompletableFuture<Boolean> slackFuture = CompletableFuture.supplyAsync(() ->
            slackService.sendCriticalAlert(title, description, details)
        );

        try {
            String pagerDutyIncidentId = pagerDutyFuture.get();
            boolean slackSent = slackFuture.get();

            log.info("Critical alert sent: pagerDuty={}, slack={}", pagerDutyIncidentId, slackSent);

            return AlertResult.builder()
                .success(pagerDutyIncidentId != null || slackSent)
                .pagerDutyIncidentId(pagerDutyIncidentId)
                .slackSent(slackSent)
                .dedupKey(dedupKey)
                .build();

        } catch (Exception e) {
            log.error("Error sending critical alert", e);
            return AlertResult.failure("Failed to send critical alert: " + e.getMessage());
        }
    }

    /**
     * Send ERROR alert to all channels
     *
     * Use for:
     * - Service degradation
     * - High error rates
     * - Database connection issues
     * - Third-party API failures
     */
    public AlertResult sendErrorAlert(String title, String description, Map<String, Object> details) {
        log.error("⚠️ ERROR ALERT: {}", title);

        String dedupKey = generateDedupKey(title, "error");

        CompletableFuture<String> pagerDutyFuture = CompletableFuture.supplyAsync(() ->
            pagerDutyService.sendErrorAlert(title, description, dedupKey, details)
        );

        CompletableFuture<Boolean> slackFuture = CompletableFuture.supplyAsync(() ->
            slackService.sendErrorAlert(title, description, details)
        );

        try {
            String pagerDutyIncidentId = pagerDutyFuture.get();
            boolean slackSent = slackFuture.get();

            return AlertResult.builder()
                .success(pagerDutyIncidentId != null || slackSent)
                .pagerDutyIncidentId(pagerDutyIncidentId)
                .slackSent(slackSent)
                .dedupKey(dedupKey)
                .build();

        } catch (Exception e) {
            log.error("Error sending error alert", e);
            return AlertResult.failure("Failed to send error alert: " + e.getMessage());
        }
    }

    /**
     * Send WARNING alert (primarily to Slack, low-priority PagerDuty)
     *
     * Use for:
     * - Performance degradation
     * - Approaching resource limits
     * - Configuration drift
     */
    public AlertResult sendWarningAlert(String title, String description, Map<String, Object> details) {
        log.warn("⚡ WARNING ALERT: {}", title);

        String dedupKey = generateDedupKey(title, "warning");

        CompletableFuture<String> pagerDutyFuture = CompletableFuture.supplyAsync(() ->
            pagerDutyService.sendWarningAlert(title, description, dedupKey, details)
        );

        CompletableFuture<Boolean> slackFuture = CompletableFuture.supplyAsync(() ->
            slackService.sendWarningAlert(title, description, details)
        );

        try {
            String pagerDutyIncidentId = pagerDutyFuture.get();
            boolean slackSent = slackFuture.get();

            return AlertResult.builder()
                .success(slackSent)
                .pagerDutyIncidentId(pagerDutyIncidentId)
                .slackSent(slackSent)
                .dedupKey(dedupKey)
                .build();

        } catch (Exception e) {
            log.error("Error sending warning alert", e);
            return AlertResult.failure("Failed to send warning alert: " + e.getMessage());
        }
    }

    /**
     * Send INFO alert (Slack only, no PagerDuty)
     */
    public AlertResult sendInfoAlert(String title, String description, Map<String, Object> details) {
        log.info("ℹ️ INFO ALERT: {}", title);

        boolean slackSent = slackService.sendInfoAlert(title, description, details);

        return AlertResult.builder()
            .success(slackSent)
            .slackSent(slackSent)
            .build();
    }

    /**
     * Send FRAUD-specific alert to fraud team channel
     */
    public AlertResult sendFraudAlert(String title, String description, Map<String, Object> details) {
        log.error("🛡️ FRAUD ALERT: {}", title);

        String dedupKey = generateDedupKey(title, "fraud");

        CompletableFuture<String> pagerDutyFuture = CompletableFuture.supplyAsync(() ->
            pagerDutyService.sendCriticalAlert("[FRAUD] " + title, description, dedupKey, details)
        );

        CompletableFuture<Boolean> slackFuture = CompletableFuture.supplyAsync(() ->
            slackService.sendFraudAlert(title, description, details)
        );

        try {
            String pagerDutyIncidentId = pagerDutyFuture.get();
            boolean slackSent = slackFuture.get();

            return AlertResult.builder()
                .success(pagerDutyIncidentId != null || slackSent)
                .pagerDutyIncidentId(pagerDutyIncidentId)
                .slackSent(slackSent)
                .dedupKey(dedupKey)
                .build();

        } catch (Exception e) {
            log.error("Error sending fraud alert", e);
            return AlertResult.failure("Failed to send fraud alert: " + e.getMessage());
        }
    }

    /**
     * Send COMPLIANCE alert
     */
    public AlertResult sendComplianceAlert(String title, String description, Map<String, Object> details) {
        log.error("⚖️ COMPLIANCE ALERT: {}", title);

        String dedupKey = generateDedupKey(title, "compliance");

        CompletableFuture<String> pagerDutyFuture = CompletableFuture.supplyAsync(() ->
            pagerDutyService.sendCriticalAlert("[COMPLIANCE] " + title, description, dedupKey, details)
        );

        CompletableFuture<Boolean> slackFuture = CompletableFuture.supplyAsync(() ->
            slackService.sendComplianceAlert(title, description, details)
        );

        try {
            String pagerDutyIncidentId = pagerDutyFuture.get();
            boolean slackSent = slackFuture.get();

            return AlertResult.builder()
                .success(pagerDutyIncidentId != null || slackSent)
                .pagerDutyIncidentId(pagerDutyIncidentId)
                .slackSent(slackSent)
                .dedupKey(dedupKey)
                .build();

        } catch (Exception e) {
            log.error("Error sending compliance alert", e);
            return AlertResult.failure("Failed to send compliance alert: " + e.getMessage());
        }
    }

    /**
     * Send SUCCESS notification (Slack only)
     */
    public AlertResult sendSuccessAlert(String title, String description, Map<String, Object> details) {
        log.info("✅ SUCCESS: {}", title);

        boolean slackSent = slackService.sendSuccessAlert(title, description, details);

        return AlertResult.builder()
            .success(slackSent)
            .slackSent(slackSent)
            .build();
    }

    /**
     * Resolve existing PagerDuty incident
     */
    public boolean resolveIncident(String dedupKey, String resolutionNote) {
        log.info("Resolving incident: dedupKey={}, note={}", dedupKey, resolutionNote);

        boolean resolved = pagerDutyService.resolveIncident(dedupKey, resolutionNote);

        if (resolved) {
            // Also send success message to Slack
            slackService.sendSuccessAlert(
                "Incident Resolved",
                resolutionNote,
                Map.of("dedup_key", dedupKey)
            );
        }

        return resolved;
    }

    /**
     * Generate deduplication key for alerts
     */
    private String generateDedupKey(String title, String severity) {
        String sanitized = title.replaceAll("[^a-zA-Z0-9-]", "-").toLowerCase();
        return String.format("%s-%s-%d", severity, sanitized, System.currentTimeMillis() / 60000);
    }

    // ==================== Alert Result ====================

    @lombok.Data
    @lombok.Builder
    public static class AlertResult {
        private boolean success;
        private String pagerDutyIncidentId;
        private boolean slackSent;
        private String dedupKey;
        private String errorMessage;

        public static AlertResult failure(String errorMessage) {
            return AlertResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
        }
    }
}
