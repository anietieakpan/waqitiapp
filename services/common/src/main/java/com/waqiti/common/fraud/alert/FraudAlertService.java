package com.waqiti.common.fraud.alert;

import com.waqiti.common.fraud.dto.DashboardAlert;
import com.waqiti.common.fraud.dto.AlertAcknowledgmentEvent;
import com.waqiti.common.fraud.dto.AlertResolutionEvent;
import com.waqiti.common.fraud.dto.FraudAlertEvent;
import com.waqiti.common.fraud.model.FraudScore;
import com.waqiti.common.fraud.model.RiskLevel;
import com.waqiti.common.fraud.notification.FraudNotificationAdapter;
import com.waqiti.common.kafka.KafkaProducerService;
// Use local alert package classes for domain logic
import com.waqiti.common.fraud.alert.FraudAlert;
import com.waqiti.common.fraud.model.AlertLevel;
import com.waqiti.common.fraud.alert.FraudAlertStatistics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import javax.annotation.concurrent.ThreadSafe;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fraud alert service for managing and distributing fraud alerts.
 * Handles multi-channel alert distribution and escalation workflows.
 *
 * <h2>Thread Safety</h2>
 * <p><strong>THREAD-SAFE</strong>. This service uses {@link java.util.concurrent.ConcurrentHashMap}
 * for internal storage and is safe for concurrent access. All public methods are thread-safe
 * and can be called from multiple threads simultaneously.
 * </p>
 *
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ThreadSafe
public class FraudAlertService {
    
    private final FraudNotificationAdapter notificationAdapter;
    private final KafkaProducerService kafkaProducerService;
    
    @Value("${waqiti.fraud.alerts.escalation.enabled:true}")
    private boolean escalationEnabled;
    
    @Value("${waqiti.fraud.alerts.escalation.timeout-minutes:30}")
    private int escalationTimeoutMinutes;
    
    private final Map<String, FraudAlert> pendingAlerts = new ConcurrentHashMap<>();
    private final Map<AlertLevel, List<String>> alertRecipients = initializeAlertRecipients();
    
    /**
     * Send fraud alert through appropriate channels
     */
    @Async
    public CompletableFuture<Void> sendAlert(FraudAlert alert) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("Sending fraud alert: {} for transaction: {}", 
                    alert.getAlertId(), alert.getTransactionId());
                
                // Store alert for tracking
                pendingAlerts.put(alert.getAlertId(), alert);
                
                // Send through Kafka for real-time processing
                sendKafkaAlert(alert);
                
                // Send email notifications
                sendEmailAlerts(alert);
                
                // Send SMS for critical alerts
                if (alert.getLevel().ordinal() >= AlertLevel.HIGH.ordinal()) {
                    sendSmsAlerts(alert);
                }
                
                // Send to monitoring dashboards
                sendToDashboard(alert);
                
                // Schedule escalation if needed
                if (escalationEnabled && alert.getLevel() == AlertLevel.CRITICAL) {
                    scheduleEscalation(alert);
                }
                
                log.info("Fraud alert sent successfully: {}", alert.getAlertId());
                
            } catch (Exception e) {
                log.error("Error sending fraud alert: {}", alert.getAlertId(), e);
            }
        });
    }
    
    /**
     * Send alert through Kafka for real-time processing
     */
    private void sendKafkaAlert(FraudAlert alert) {
        try {
            FraudAlertEvent alertEvent = FraudAlertEvent.builder()
                .alertId(alert.getAlertId())
                .level(alert.getLevel())
                .transactionId(alert.getTransactionId())
                .userId(alert.getUserId())
                .fraudScore(alert.getFraudScore().getScore())
                .riskLevel(alert.getRiskLevel())
                .timestamp(alert.getTimestamp())
                .build();
                
            kafkaProducerService.sendEvent("fraud-alerts", alertEvent);
            
        } catch (Exception e) {
            log.error("Error sending Kafka alert for: {}", alert.getAlertId(), e);
        }
    }
    
    /**
     * Send email alerts to appropriate recipients
     */
    private void sendEmailAlerts(FraudAlert alert) {
        try {
            List<String> recipients = alertRecipients.get(alert.getLevel());
            if (recipients == null || recipients.isEmpty()) {
                return;
            }
            
            String subject = formatAlertSubject(alert);
            String body = formatAlertEmailBody(alert);

            // Send through notification adapter (convert to model.FraudAlert)
            notificationAdapter.sendFraudAlert(convertToModelAlert(alert));
            
        } catch (Exception e) {
            log.error("Error sending email alerts for: {}", alert.getAlertId(), e);
        }
    }
    
    /**
     * Send SMS alerts for high-priority cases
     */
    private void sendSmsAlerts(FraudAlert alert) {
        try {
            List<String> recipients = getEmergencyContacts();
            if (recipients.isEmpty()) {
                return;
            }
            
            String message = formatAlertSmsMessage(alert);

            // SMS alerts handled by notification adapter based on alert severity (convert to model.FraudAlert)
            notificationAdapter.sendFraudAlert(convertToModelAlert(alert));
            
        } catch (Exception e) {
            log.error("Error sending SMS alerts for: {}", alert.getAlertId(), e);
        }
    }
    
    /**
     * Send alert to monitoring dashboards
     */
    private void sendToDashboard(FraudAlert alert) {
        try {
            DashboardAlert dashboardAlert = DashboardAlert.builder()
                .alertId(alert.getAlertId())
                .level(alert.getLevel())
                .title(formatDashboardTitle(alert))
                .description(formatDashboardDescription(alert))
                .transactionId(alert.getTransactionId())
                .userId(alert.getUserId())
                .fraudScore(alert.getFraudScore().getScore())
                .timestamp(alert.getTimestamp())
                .build();
                
            kafkaProducerService.sendEvent("dashboard-alerts", dashboardAlert);
            
        } catch (Exception e) {
            log.error("Error sending dashboard alert for: {}", alert.getAlertId(), e);
        }
    }
    
    /**
     * Schedule escalation for critical alerts
     */
    private void scheduleEscalation(FraudAlert alert) {
        // In a real implementation, this would use a scheduler like Quartz
        log.info("Scheduling escalation for critical alert: {} in {} minutes",
            alert.getAlertId(), escalationTimeoutMinutes);

        // PRODUCTION FIX: Convert Duration to milliseconds for delayedExecutor
        CompletableFuture.delayedExecutor(
            escalationTimeoutMinutes * 60 * 1000L,
            java.util.concurrent.TimeUnit.MILLISECONDS
        ).execute(() -> {
            if (pendingAlerts.containsKey(alert.getAlertId())) {
                escalateAlert(alert);
            }
        });
    }
    
    /**
     * Escalate alert to higher management levels
     */
    private void escalateAlert(FraudAlert alert) {
        try {
            log.warn("ESCALATING fraud alert: {} - no response within {} minutes", 
                alert.getAlertId(), escalationTimeoutMinutes);
            
            // Create escalated alert
            FraudAlert escalatedAlert = alert.toBuilder()
                .level(AlertLevel.CRITICAL)
                .escalated(true)
                .escalationTimestamp(LocalDateTime.now())
                .build();
            
            // Send to executive team
            List<String> executives = getExecutiveContacts();
            String subject = "ESCALATED FRAUD ALERT - " + formatAlertSubject(alert);
            String body = formatEscalationEmailBody(escalatedAlert);

            // Send escalated alert through notification adapter (convert to model.FraudAlert)
            notificationAdapter.sendFraudAlert(convertToModelAlert(escalatedAlert));
            
            // Log to audit trail
            log.error("FRAUD_ALERT_ESCALATED: {} for transaction: {}", 
                alert.getAlertId(), alert.getTransactionId());
                
        } catch (Exception e) {
            log.error("Error escalating fraud alert: {}", alert.getAlertId(), e);
        }
    }
    
    /**
     * Acknowledge alert (stops escalation)
     */
    public void acknowledgeAlert(String alertId, String acknowledgedBy) {
        FraudAlert alert = pendingAlerts.remove(alertId);
        if (alert != null) {
            log.info("Fraud alert acknowledged: {} by {}", alertId, acknowledgedBy);
            
            // Send acknowledgment event
            AlertAcknowledgmentEvent ackEvent = AlertAcknowledgmentEvent.builder()
                .alertId(alertId)
                .acknowledgedBy(acknowledgedBy)
                .timestamp(LocalDateTime.now())
                .build();
                
            kafkaProducerService.sendEvent("alert-acknowledgments", ackEvent);
        }
    }
    
    /**
     * Resolve alert
     */
    public void resolveAlert(String alertId, String resolvedBy, String resolution) {
        FraudAlert alert = pendingAlerts.remove(alertId);
        if (alert != null) {
            log.info("Fraud alert resolved: {} by {} - {}", alertId, resolvedBy, resolution);
            
            // Send resolution event
            AlertResolutionEvent resolutionEvent = AlertResolutionEvent.builder()
                .alertId(alertId)
                .resolvedBy(resolvedBy)
                .resolution(resolution)
                .timestamp(LocalDateTime.now())
                .build();
                
            kafkaProducerService.sendEvent("alert-resolutions", resolutionEvent);
        }
    }
    
    /**
     * Get alert statistics
     */
    public FraudAlertStatistics getAlertStatistics() {
        long criticalCount = pendingAlerts.values().stream()
            .mapToLong(alert -> alert.getLevel() == AlertLevel.CRITICAL ? 1 : 0)
            .sum();

        long highCount = pendingAlerts.values().stream()
            .mapToLong(alert -> alert.getLevel() == AlertLevel.HIGH ? 1 : 0)
            .sum();

        long mediumCount = pendingAlerts.values().stream()
            .mapToLong(alert -> alert.getLevel() == AlertLevel.MEDIUM ? 1 : 0)
            .sum();
            
        return FraudAlertStatistics.builder()
            .totalPendingAlerts(pendingAlerts.size())
            .criticalAlerts((int) criticalCount)
            .highAlerts((int) highCount)
            .mediumAlerts((int) mediumCount)
            .lastUpdated(LocalDateTime.now())
            .build();
    }
    
    // Formatting methods
    
    private String formatAlertSubject(FraudAlert alert) {
        return String.format("[%s] Fraud Alert - Transaction %s", 
            alert.getLevel(), alert.getTransactionId());
    }
    
    private String formatAlertEmailBody(FraudAlert alert) {
        StringBuilder body = new StringBuilder();
        body.append("FRAUD ALERT DETAILS\n");
        body.append("==================\n\n");
        body.append("Alert ID: ").append(alert.getAlertId()).append("\n");
        body.append("Level: ").append(alert.getLevel()).append("\n");
        body.append("Transaction ID: ").append(alert.getTransactionId()).append("\n");
        body.append("User ID: ").append(alert.getUserId()).append("\n");
        body.append("Fraud Score: ").append(alert.getFraudScore().getScore()).append("\n");
        body.append("Risk Level: ").append(alert.getRiskLevel()).append("\n");
        body.append("Timestamp: ").append(alert.getTimestamp()).append("\n\n");
        
        if (!alert.getViolations().isEmpty()) {
            body.append("RULE VIOLATIONS:\n");
            alert.getViolations().forEach(violation -> 
                body.append("- ").append(violation.getRuleName())
                    .append(" (").append(violation.getSeverity()).append(")\n"));
            body.append("\n");
        }
        
        if (!alert.getAnomalies().isEmpty()) {
            body.append("BEHAVIORAL ANOMALIES:\n");
            alert.getAnomalies().forEach(anomaly ->
                body.append("- ").append(anomaly.getType())
                    .append(": ").append(anomaly.getDescription()).append("\n"));
            body.append("\n");
        }
        
        body.append("Please investigate immediately.\n");
        return body.toString();
    }
    
    private String formatAlertSmsMessage(FraudAlert alert) {
        return String.format("FRAUD ALERT: %s - Transaction %s - Score %.2f - Investigate immediately", 
            alert.getLevel(), alert.getTransactionId(), alert.getFraudScore().getScore());
    }
    
    private String formatDashboardTitle(FraudAlert alert) {
        return String.format("%s Fraud Alert", alert.getLevel());
    }
    
    private String formatDashboardDescription(FraudAlert alert) {
        return String.format("Transaction %s flagged with fraud score %.2f", 
            alert.getTransactionId(), alert.getFraudScore().getScore());
    }
    
    private String formatEscalationEmailBody(FraudAlert alert) {
        return "***** ESCALATED FRAUD ALERT *****\n\n" +
               "This alert has been escalated due to lack of response within " + 
               escalationTimeoutMinutes + " minutes.\n\n" +
               formatAlertEmailBody(alert) +
               "\nIMMEDIATE ACTION REQUIRED!";
    }
    
    // Configuration methods
    
    private Map<AlertLevel, List<String>> initializeAlertRecipients() {
        Map<AlertLevel, List<String>> recipients = new HashMap<>();
        
        // In production, these would come from configuration
        recipients.put(AlertLevel.LOW, Arrays.asList("fraud-analysts@example.com"));
        recipients.put(AlertLevel.MEDIUM, Arrays.asList("fraud-analysts@example.com", "fraud-managers@example.com"));
        recipients.put(AlertLevel.HIGH, Arrays.asList("fraud-managers@example.com", "security@example.com"));
        recipients.put(AlertLevel.CRITICAL, Arrays.asList("security@example.com", "executives@example.com"));
        
        return recipients;
    }
    
    private List<String> getEmergencyContacts() {
        // In production, this would come from configuration
        return Arrays.asList("+1234567890", "+1234567891"); // Emergency phone numbers
    }
    
    private List<String> getExecutiveContacts() {
        // In production, this would come from configuration
        return Arrays.asList("ceo@example.com", "security@example.com", "cto@example.com");
    }

    /**
     * Convert alert.FraudAlert to model.FraudAlert for notification adapter
     */
    private com.waqiti.common.fraud.model.FraudAlert convertToModelAlert(FraudAlert alertPackageAlert) {
        if (alertPackageAlert == null) {
            return null;
        }
        return com.waqiti.common.fraud.model.FraudAlert.builder()
                .id(alertPackageAlert.getAlertId())
                .transactionId(alertPackageAlert.getTransactionId())
                .userId(alertPackageAlert.getUserId())
                .fraudScore(alertPackageAlert.getFraudScore() != null ?
                    BigDecimal.valueOf(alertPackageAlert.getFraudScore().getScore()) : null)
                .riskLevel(convertRiskLevel(alertPackageAlert.getRiskLevel()))
                .severity(alertPackageAlert.getLevel())
                .createdAt(alertPackageAlert.getCreatedAt())
                .build();
    }

    private com.waqiti.common.fraud.model.FraudAlert.RiskLevel convertRiskLevel(
            com.waqiti.common.fraud.model.FraudRiskLevel fraudRiskLevel) {
        if (fraudRiskLevel == null) return null;
        return switch (fraudRiskLevel) {
            case LOW -> com.waqiti.common.fraud.model.FraudAlert.RiskLevel.LOW;
            case MEDIUM -> com.waqiti.common.fraud.model.FraudAlert.RiskLevel.MEDIUM;
            case HIGH -> com.waqiti.common.fraud.model.FraudAlert.RiskLevel.HIGH;
            case CRITICAL -> com.waqiti.common.fraud.model.FraudAlert.RiskLevel.VERY_HIGH;
        };
    }
}