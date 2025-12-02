package com.waqiti.common.alerting;

import com.waqiti.common.alerting.model.Alert;
import com.waqiti.common.alerting.model.AlertSeverity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Production-grade Alert Audit Service
 *
 * Provides comprehensive audit logging for all alert operations
 * Tracks alert lifecycle, delivery status, and failures
 *
 * @author Waqiti Engineering
 * @version 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertAuditService {

    // Metrics counters for alert statistics
    private final AtomicLong totalAlertsSent = new AtomicLong(0);
    private final AtomicLong deduplicatedCount = new AtomicLong(0);
    private final AtomicLong suppressedCount = new AtomicLong(0);
    private final AtomicLong failedDeliveryCount = new AtomicLong(0);
    private final Map<AlertSeverity, AtomicLong> alertCountBySeverity = new HashMap<>();

    /**
     * Audit alert sent
     */
    public void auditAlertSent(Alert alert, String channel) {
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("alertId", alert.getId());
        auditData.put("severity", alert.getSeverity().name());
        auditData.put("source", alert.getSource());
        auditData.put("channel", channel);
        auditData.put("timestamp", Instant.now());

        log.info("ALERT_SENT: id={}, severity={}, source={}, channel={}",
            alert.getId(), alert.getSeverity(), alert.getSource(), channel);
    }

    /**
     * Audit alert failed
     */
    public void auditAlertFailed(Alert alert, String channel, String reason) {
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("alertId", alert.getId());
        auditData.put("severity", alert.getSeverity().name());
        auditData.put("source", alert.getSource());
        auditData.put("channel", channel);
        auditData.put("reason", reason);
        auditData.put("timestamp", Instant.now());

        log.error("ALERT_FAILED: id={}, severity={}, source={}, channel={}, reason={}",
            alert.getId(), alert.getSeverity(), alert.getSource(), channel, reason);
    }

    /**
     * Audit alert deduplicated
     */
    public void auditAlertDeduplicated(Alert alert) {
        deduplicatedCount.incrementAndGet();
        log.debug("ALERT_DEDUPLICATED: id={}, severity={}, source={}",
            alert.getId(), alert.getSeverity(), alert.getSource());
    }

    /**
     * Log alert suppressed
     */
    public void logAlertSuppressed(Alert alert, String reason) {
        suppressedCount.incrementAndGet();
        log.debug("ALERT_SUPPRESSED: id={}, severity={}, source={}, reason={}",
            alert.getId(), alert.getSeverity(), alert.getSource(), reason);
    }

    /**
     * Log alert deduplicated (alternate signature)
     */
    public void logAlertDeduplicated(Alert alert) {
        auditAlertDeduplicated(alert);
    }

    /**
     * Log alert delivered
     */
    public void logAlertDelivered(Alert alert, String channel, String deliveryId) {
        totalAlertsSent.incrementAndGet();
        alertCountBySeverity.computeIfAbsent(alert.getSeverity(), k -> new AtomicLong(0)).incrementAndGet();
        log.info("ALERT_DELIVERED: id={}, severity={}, channel={}, deliveryId={}",
            alert.getId(), alert.getSeverity(), channel, deliveryId);
    }

    /**
     * Log alert delivery failed
     */
    public void logAlertDeliveryFailed(Alert alert, String channel, String error) {
        failedDeliveryCount.incrementAndGet();
        log.error("ALERT_DELIVERY_FAILED: id={}, severity={}, channel={}, error={}",
            alert.getId(), alert.getSeverity(), channel, error);
    }

    /**
     * Log alert completed
     */
    public void logAlertCompleted(Alert alert) {
        log.info("ALERT_COMPLETED: id={}, severity={}, source={}",
            alert.getId(), alert.getSeverity(), alert.getSource());
    }

    /**
     * Log alert error
     */
    public void logAlertError(Alert alert, String error) {
        log.error("ALERT_ERROR: id={}, severity={}, source={}, error={}",
            alert.getId(), alert.getSeverity(), alert.getSource(), error);
    }

    /**
     * Log alert resolved
     */
    public void logAlertResolved(String alertId, String resolvedBy) {
        log.info("ALERT_RESOLVED: id={}, resolvedBy={}", alertId, resolvedBy);
    }

    /**
     * Log alert acknowledged
     */
    public void logAlertAcknowledged(String alertId, String acknowledgedBy) {
        log.info("ALERT_ACKNOWLEDGED: id={}, acknowledgedBy={}", alertId, acknowledgedBy);
    }

    /**
     * Log maintenance mode enabled
     */
    public void logMaintenanceModeEnabled(String reason, long durationMinutes) {
        log.warn("MAINTENANCE_MODE_ENABLED: reason={}, durationMinutes={}", reason, durationMinutes);
    }

    /**
     * Log maintenance mode disabled
     */
    public void logMaintenanceModeDisabled(String reason) {
        log.info("MAINTENANCE_MODE_DISABLED: reason={}", reason);
    }

    /**
     * Log alert type suppressed
     */
    public void logAlertTypeSuppressed(String alertType, long durationMinutes) {
        log.info("ALERT_TYPE_SUPPRESSED: type={}, durationMinutes={}", alertType, durationMinutes);
    }

    /**
     * Log alert type unsuppressed
     */
    public void logAlertTypeUnsuppressed(String alertType) {
        log.info("ALERT_TYPE_UNSUPPRESSED: type={}", alertType);
    }

    /**
     * Get total alerts sent
     */
    public long getTotalAlertsSent() {
        return totalAlertsSent.get();
    }

    /**
     * Get alert count by severity
     */
    public long getAlertCountBySeverity(AlertSeverity severity) {
        return alertCountBySeverity.getOrDefault(severity, new AtomicLong(0)).get();
    }

    /**
     * Get deduplicated count
     */
    public long getDeduplicatedCount() {
        return deduplicatedCount.get();
    }

    /**
     * Get suppressed count
     */
    public long getSuppressedCount() {
        return suppressedCount.get();
    }

    /**
     * Get failed delivery count
     */
    public long getFailedDeliveryCount() {
        return failedDeliveryCount.get();
    }
}
