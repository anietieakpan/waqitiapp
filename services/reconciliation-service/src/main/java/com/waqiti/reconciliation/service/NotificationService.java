package com.waqiti.reconciliation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for sending notifications about reconciliation events
 * Handles alerts, reports, and status updates
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${reconciliation.notifications.topic:reconciliation-notifications}")
    private String notificationTopic;

    @Value("${reconciliation.notifications.alerts.topic:reconciliation-alerts}")
    private String alertTopic;

    @Value("${reconciliation.notifications.enabled:true}")
    private boolean notificationsEnabled;

    /**
     * Send a general alert notification
     */
    public void sendAlert(String title, String message) {
        if (!notificationsEnabled) {
            log.debug("Notifications disabled - would send alert: {} - {}", title, message);
            return;
        }

        try {
            Map<String, Object> alert = createBaseNotification("ALERT", title, message);
            alert.put("severity", "HIGH");
            alert.put("requiresAction", true);

            kafkaTemplate.send(alertTopic, alert);
            log.info("Alert notification sent: {}", title);

        } catch (Exception e) {
            log.error("Failed to send alert notification: {} - {}", title, message, e);
        }
    }

    /**
     * Send reconciliation completion notification
     */
    public void sendReconciliationCompleted(String reconciliationId, int matchedCount, 
                                          int discrepancyCount, int pendingCount) {
        if (!notificationsEnabled) {
            return;
        }

        try {
            Map<String, Object> notification = createBaseNotification(
                "RECONCILIATION_COMPLETED",
                "Reconciliation Completed",
                String.format("Reconciliation %s completed - Matched: %d, Discrepancies: %d, Pending: %d",
                    reconciliationId, matchedCount, discrepancyCount, pendingCount)
            );

            notification.put("reconciliationId", reconciliationId);
            notification.put("matchedCount", matchedCount);
            notification.put("discrepancyCount", discrepancyCount);
            notification.put("pendingCount", pendingCount);
            notification.put("severity", discrepancyCount > 0 ? "MEDIUM" : "LOW");

            kafkaTemplate.send(notificationTopic, notification);
            log.info("Reconciliation completion notification sent for: {}", reconciliationId);

        } catch (Exception e) {
            log.error("Failed to send reconciliation completion notification for: {}", reconciliationId, e);
        }
    }

    /**
     * Send reconciliation failed notification
     */
    public void sendReconciliationFailed(String reconciliationId, String error) {
        if (!notificationsEnabled) {
            return;
        }

        try {
            Map<String, Object> notification = createBaseNotification(
                "RECONCILIATION_FAILED",
                "Reconciliation Failed",
                String.format("Reconciliation %s failed: %s", reconciliationId, error)
            );

            notification.put("reconciliationId", reconciliationId);
            notification.put("error", error);
            notification.put("severity", "HIGH");
            notification.put("requiresAction", true);

            kafkaTemplate.send(alertTopic, notification);
            log.error("Reconciliation failure notification sent for: {} - {}", reconciliationId, error);

        } catch (Exception e) {
            log.error("Failed to send reconciliation failure notification for: {}", reconciliationId, e);
        }
    }

    /**
     * Send high discrepancy alert
     */
    public void sendHighDiscrepancyAlert(String reconciliationId, int discrepancyCount, 
                                       java.math.BigDecimal totalAmount) {
        if (!notificationsEnabled) {
            return;
        }

        try {
            Map<String, Object> alert = createBaseNotification(
                "HIGH_DISCREPANCY_ALERT",
                "High Discrepancy Alert",
                String.format("Reconciliation %s found %d discrepancies totaling %s",
                    reconciliationId, discrepancyCount, totalAmount)
            );

            alert.put("reconciliationId", reconciliationId);
            alert.put("discrepancyCount", discrepancyCount);
            alert.put("totalAmount", totalAmount);
            alert.put("severity", "HIGH");
            alert.put("requiresAction", true);

            kafkaTemplate.send(alertTopic, alert);
            log.warn("High discrepancy alert sent for reconciliation: {}", reconciliationId);

        } catch (Exception e) {
            log.error("Failed to send high discrepancy alert for: {}", reconciliationId, e);
        }
    }

    /**
     * Send orphaned transaction alert
     */
    public void sendOrphanedTransactionAlert(String transactionId, String type, 
                                           java.math.BigDecimal amount) {
        if (!notificationsEnabled) {
            return;
        }

        try {
            Map<String, Object> alert = createBaseNotification(
                "ORPHANED_TRANSACTION_ALERT",
                "Orphaned Transaction Alert",
                String.format("Orphaned %s transaction found: %s (Amount: %s)",
                    type, transactionId, amount)
            );

            alert.put("transactionId", transactionId);
            alert.put("transactionType", type);
            alert.put("amount", amount);
            alert.put("severity", amount.compareTo(new java.math.BigDecimal("1000")) > 0 ? "HIGH" : "MEDIUM");
            alert.put("requiresAction", true);

            kafkaTemplate.send(alertTopic, alert);
            log.warn("Orphaned transaction alert sent for: {} ({})", transactionId, type);

        } catch (Exception e) {
            log.error("Failed to send orphaned transaction alert for: {}", transactionId, e);
        }
    }

    /**
     * Send balance mismatch alert
     */
    public void sendBalanceMismatchAlert(String accountId, java.math.BigDecimal expected, 
                                       java.math.BigDecimal actual) {
        if (!notificationsEnabled) {
            return;
        }

        try {
            java.math.BigDecimal difference = expected.subtract(actual).abs();
            
            Map<String, Object> alert = createBaseNotification(
                "BALANCE_MISMATCH_ALERT",
                "Balance Mismatch Alert",
                String.format("Balance mismatch for account %s - Expected: %s, Actual: %s, Difference: %s",
                    accountId, expected, actual, difference)
            );

            alert.put("accountId", accountId);
            alert.put("expectedBalance", expected);
            alert.put("actualBalance", actual);
            alert.put("difference", difference);
            alert.put("severity", difference.compareTo(new java.math.BigDecimal("100")) > 0 ? "HIGH" : "MEDIUM");
            alert.put("requiresAction", true);

            kafkaTemplate.send(alertTopic, alert);
            log.warn("Balance mismatch alert sent for account: {}", accountId);

        } catch (Exception e) {
            log.error("Failed to send balance mismatch alert for account: {}", accountId, e);
        }
    }

    /**
     * Send reconciliation progress notification
     */
    public void sendReconciliationProgress(String reconciliationId, int processed, 
                                         int total, String phase) {
        if (!notificationsEnabled) {
            return;
        }

        try {
            double percentage = total > 0 ? (processed * 100.0) / total : 0.0;
            
            Map<String, Object> notification = createBaseNotification(
                "RECONCILIATION_PROGRESS",
                "Reconciliation Progress",
                String.format("Reconciliation %s progress: %.1f%% (%d/%d) - %s",
                    reconciliationId, percentage, processed, total, phase)
            );

            notification.put("reconciliationId", reconciliationId);
            notification.put("processedCount", processed);
            notification.put("totalCount", total);
            notification.put("percentage", percentage);
            notification.put("currentPhase", phase);
            notification.put("severity", "LOW");

            // Only send progress notifications for every 10% or phase changes
            if (processed % Math.max(1, total / 10) == 0 || phase != null) {
                kafkaTemplate.send(notificationTopic, notification);
                log.debug("Reconciliation progress notification sent: {} - {:.1f}%", 
                    reconciliationId, percentage);
            }

        } catch (Exception e) {
            log.error("Failed to send reconciliation progress notification for: {}", reconciliationId, e);
        }
    }

    /**
     * Create base notification structure
     */
    private Map<String, Object> createBaseNotification(String type, String title, String message) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", type);
        notification.put("title", title);
        notification.put("message", message);
        notification.put("timestamp", LocalDateTime.now());
        notification.put("service", "reconciliation-service");
        notification.put("version", "1.0");
        return notification;
    }

    /**
     * Check if notifications are enabled
     */
    public boolean isNotificationsEnabled() {
        return notificationsEnabled;
    }

    /**
     * Enable or disable notifications
     */
    public void setNotificationsEnabled(boolean enabled) {
        this.notificationsEnabled = enabled;
        log.info("Notifications {}", enabled ? "enabled" : "disabled");
    }
}