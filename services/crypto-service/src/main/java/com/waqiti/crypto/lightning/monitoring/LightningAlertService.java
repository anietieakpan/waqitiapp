package com.waqiti.crypto.lightning.monitoring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Lightning Network Alert Service
 * Handles alert generation, notification, and escalation
 * Supports multiple notification channels: Slack, email, webhooks, SMS
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LightningAlertService {

    private final JavaMailSender mailSender;
    private final RestTemplate restTemplate;

    @Value("${waqiti.lightning.alerts.enabled:true}")
    private boolean alertsEnabled;

    @Value("${waqiti.lightning.alerts.slack.webhook-url:}")
    private String slackWebhookUrl;

    @Value("${waqiti.lightning.alerts.slack.channel:}")
    private String slackChannel;

    @Value("${waqiti.lightning.alerts.email.enabled:true}")
    private boolean emailAlertsEnabled;

    @Value("${waqiti.lightning.alerts.email.to:}")
    private List<String> emailRecipients;

    @Value("${waqiti.lightning.alerts.email.from:noreply@example.com}")
    private String emailFromAddress;

    @Value("${waqiti.lightning.alerts.webhook.urls:}")
    private List<String> webhookUrls;

    @Value("${waqiti.lightning.alerts.sms.enabled:false}")
    private boolean smsAlertsEnabled;

    @Value("${waqiti.lightning.alerts.sms.service-url:}")
    private String smsServiceUrl;

    @Value("${waqiti.lightning.alerts.sms.api-key:}")
    private String smsApiKey;

    @Value("${waqiti.lightning.alerts.sms.recipients:}")
    private List<String> smsRecipients;

    @Value("${waqiti.lightning.alerts.escalation.enabled:true}")
    private boolean escalationEnabled;

    @Value("${waqiti.lightning.alerts.escalation.critical-repeat-minutes:15}")
    private int criticalAlertRepeatMinutes;

    @Value("${waqiti.lightning.alerts.escalation.high-repeat-minutes:60}")
    private int highAlertRepeatMinutes;

    @Value("${waqiti.lightning.alerts.rate-limit.max-per-hour:50}")
    private int maxAlertsPerHour;

    @Value("${waqiti.lightning.alerts.rate-limit.max-per-type-hour:10}")
    private int maxAlertsPerTypePerHour;

    // Alert tracking and rate limiting
    private final Map<String, AlertHistory> alertHistory = new ConcurrentHashMap<>();
    private final Map<String, Integer> hourlyAlertCounts = new ConcurrentHashMap<>();
    private final Map<String, Integer> hourlyAlertCountsByType = new ConcurrentHashMap<>();
    private final ScheduledExecutorService alertExecutor = Executors.newScheduledThreadPool(3);

    @PostConstruct
    public void init() {
        if (!alertsEnabled) {
            log.info("Lightning alerts are disabled");
            return;
        }

        log.info("Initializing Lightning Alert Service");
        
        // Start alert cleanup task
        alertExecutor.scheduleAtFixedRate(this::cleanupOldAlerts, 1, 1, TimeUnit.HOURS);
        
        // Start escalation task
        if (escalationEnabled) {
            alertExecutor.scheduleAtFixedRate(this::processEscalations, 5, 5, TimeUnit.MINUTES);
        }
        
        log.info("Lightning Alert Service initialized successfully");
    }

    // ============ MAIN ALERT METHODS ============

    /**
     * Send a Lightning Network alert
     */
    public void sendAlert(AlertSeverity severity, String alertType, String message, Map<String, Object> metadata) {
        if (!alertsEnabled) {
            log.debug("Alerts disabled, skipping alert: {}", alertType);
            return;
        }

        try {
            // Check rate limiting
            if (isRateLimited(alertType)) {
                log.warn("Alert rate limited: {} - {}", alertType, message);
                return;
            }

            // Create alert
            LightningAlert alert = LightningAlert.builder()
                .id(UUID.randomUUID().toString())
                .severity(severity)
                .alertType(alertType)
                .message(message)
                .metadata(metadata != null ? metadata : new HashMap<>())
                .timestamp(Instant.now())
                .source("waqiti-crypto-service")
                .environment(System.getProperty("spring.profiles.active", "development"))
                .build();

            log.info("Sending Lightning alert: {} - {} - {}", severity, alertType, message);

            // Record alert history
            recordAlertHistory(alert);

            // Send notifications based on severity
            sendNotifications(alert);

            // Schedule escalation if needed
            scheduleEscalation(alert);

        } catch (Exception e) {
            log.error("Failed to send Lightning alert: {} - {}", alertType, message, e);
        }
    }

    /**
     * Send health status change alert
     */
    public void sendHealthStatusAlert(LightningMonitoringService.HealthStatus previousStatus, 
                                    LightningMonitoringService.HealthStatus currentStatus) {
        
        AlertSeverity severity = determineHealthAlertSeverity(currentStatus);
        String alertType = "HEALTH_STATUS_CHANGE";
        String message = String.format("Lightning Network health status changed from %s to %s", 
            previousStatus, currentStatus);
        
        Map<String, Object> metadata = Map.of(
            "previous_status", previousStatus.toString(),
            "current_status", currentStatus.toString(),
            "timestamp", Instant.now().toString()
        );

        sendAlert(severity, alertType, message, metadata);
    }

    /**
     * Send system startup alert
     */
    public void sendSystemStartupAlert() {
        sendAlert(
            AlertSeverity.INFO,
            "SYSTEM_STARTUP",
            "Lightning Network service has started successfully",
            Map.of(
                "startup_time", Instant.now().toString(),
                "host", getHostname()
            )
        );
    }

    /**
     * Send system shutdown alert
     */
    public void sendSystemShutdownAlert() {
        sendAlert(
            AlertSeverity.WARNING,
            "SYSTEM_SHUTDOWN",
            "Lightning Network service is shutting down",
            Map.of(
                "shutdown_time", Instant.now().toString(),
                "host", getHostname()
            )
        );
    }

    // ============ NOTIFICATION METHODS ============

    private void sendNotifications(LightningAlert alert) {
        // Send based on severity level
        switch (alert.getSeverity()) {
            case CRITICAL:
                sendSlackNotification(alert);
                sendEmailNotification(alert);
                sendWebhookNotification(alert);
                if (smsAlertsEnabled) {
                    sendSmsNotification(alert);
                }
                break;
                
            case HIGH:
                sendSlackNotification(alert);
                sendEmailNotification(alert);
                sendWebhookNotification(alert);
                break;
                
            case MEDIUM:
                sendSlackNotification(alert);
                if (alert.getAlertType().contains("SECURITY") || 
                    alert.getAlertType().contains("COMPLIANCE")) {
                    sendEmailNotification(alert);
                }
                sendWebhookNotification(alert);
                break;
                
            case LOW:
            case INFO:
                sendSlackNotification(alert);
                sendWebhookNotification(alert);
                break;
        }
    }

    private void sendSlackNotification(LightningAlert alert) {
        if (slackWebhookUrl == null || slackWebhookUrl.isEmpty()) {
            log.debug("Slack webhook URL not configured, skipping Slack notification");
            return;
        }

        try {
            String color = getSlackColor(alert.getSeverity());
            String emoji = getAlertEmoji(alert.getSeverity());
            
            Map<String, Object> slackPayload = Map.of(
                "channel", slackChannel.isEmpty() ? "#alerts" : slackChannel,
                "username", "Lightning Network Alerts",
                "icon_emoji", ":zap:",
                "attachments", List.of(Map.of(
                    "color", color,
                    "title", emoji + " " + alert.getAlertType(),
                    "text", alert.getMessage(),
                    "fields", List.of(
                        Map.of("title", "Severity", "value", alert.getSeverity().toString(), "short", true),
                        Map.of("title", "Environment", "value", alert.getEnvironment(), "short", true),
                        Map.of("title", "Timestamp", "value", alert.getTimestamp().toString(), "short", true),
                        Map.of("title", "Source", "value", alert.getSource(), "short", true)
                    ),
                    "footer", "Waqiti Lightning Network",
                    "ts", alert.getTimestamp().getEpochSecond()
                ))
            );

            restTemplate.postForEntity(slackWebhookUrl, slackPayload, String.class);
            log.debug("Slack notification sent for alert: {}", alert.getId());

        } catch (Exception e) {
            log.error("Failed to send Slack notification for alert: {}", alert.getId(), e);
        }
    }

    private void sendEmailNotification(LightningAlert alert) {
        if (!emailAlertsEnabled || emailRecipients.isEmpty()) {
            log.debug("Email alerts disabled or no recipients configured");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(emailFromAddress);
            message.setTo(emailRecipients.toArray(new String[0]));
            message.setSubject(String.format("[%s] Lightning Network Alert - %s", 
                alert.getSeverity(), alert.getAlertType()));
            
            StringBuilder emailBody = new StringBuilder();
            emailBody.append("Lightning Network Alert\n");
            emailBody.append("========================\n\n");
            emailBody.append("Alert Type: ").append(alert.getAlertType()).append("\n");
            emailBody.append("Severity: ").append(alert.getSeverity()).append("\n");
            emailBody.append("Message: ").append(alert.getMessage()).append("\n");
            emailBody.append("Timestamp: ").append(alert.getTimestamp()).append("\n");
            emailBody.append("Environment: ").append(alert.getEnvironment()).append("\n");
            emailBody.append("Source: ").append(alert.getSource()).append("\n");
            emailBody.append("Alert ID: ").append(alert.getId()).append("\n\n");
            
            if (!alert.getMetadata().isEmpty()) {
                emailBody.append("Additional Details:\n");
                emailBody.append("===================\n");
                alert.getMetadata().forEach((key, value) -> 
                    emailBody.append(key).append(": ").append(value).append("\n"));
            }
            
            emailBody.append("\n");
            emailBody.append("Generated by Waqiti Lightning Network Monitoring\n");
            emailBody.append("Timestamp: ").append(Instant.now()).append("\n");

            message.setText(emailBody.toString());
            mailSender.send(message);
            
            log.debug("Email notification sent for alert: {}", alert.getId());

        } catch (Exception e) {
            log.error("Failed to send email notification for alert: {}", alert.getId(), e);
        }
    }

    private void sendWebhookNotification(LightningAlert alert) {
        if (webhookUrls.isEmpty()) {
            log.debug("No webhook URLs configured");
            return;
        }

        for (String webhookUrl : webhookUrls) {
            try {
                Map<String, Object> webhookPayload = Map.of(
                    "alert_id", alert.getId(),
                    "severity", alert.getSeverity().toString(),
                    "alert_type", alert.getAlertType(),
                    "message", alert.getMessage(),
                    "timestamp", alert.getTimestamp().toString(),
                    "source", alert.getSource(),
                    "environment", alert.getEnvironment(),
                    "metadata", alert.getMetadata()
                );

                restTemplate.postForEntity(webhookUrl, webhookPayload, String.class);
                log.debug("Webhook notification sent to {} for alert: {}", webhookUrl, alert.getId());

            } catch (Exception e) {
                log.error("Failed to send webhook notification to {} for alert: {}", 
                    webhookUrl, alert.getId(), e);
            }
        }
    }

    private void sendSmsNotification(LightningAlert alert) {
        if (!smsAlertsEnabled || smsRecipients.isEmpty() || 
            smsServiceUrl.isEmpty() || smsApiKey.isEmpty()) {
            log.debug("SMS alerts not configured properly");
            return;
        }

        try {
            String smsMessage = String.format("[%s] Lightning Alert: %s - %s", 
                alert.getSeverity(), alert.getAlertType(), alert.getMessage());
            
            // Truncate message if too long for SMS
            if (smsMessage.length() > 160) {
                smsMessage = smsMessage.substring(0, 157) + "...";
            }

            for (String recipient : smsRecipients) {
                Map<String, Object> smsPayload = Map.of(
                    "to", recipient,
                    "message", smsMessage,
                    "api_key", smsApiKey
                );

                restTemplate.postForEntity(smsServiceUrl, smsPayload, String.class);
                log.debug("SMS notification sent to {} for alert: {}", recipient, alert.getId());
            }

        } catch (Exception e) {
            log.error("Failed to send SMS notification for alert: {}", alert.getId(), e);
        }
    }

    // ============ RATE LIMITING ============

    private boolean isRateLimited(String alertType) {
        String currentHour = getCurrentHourKey();
        
        // Check global rate limit
        int totalAlerts = hourlyAlertCounts.getOrDefault(currentHour, 0);
        if (totalAlerts >= maxAlertsPerHour) {
            return true;
        }
        
        // Check per-type rate limit
        String typeHourKey = alertType + ":" + currentHour;
        int typeAlerts = hourlyAlertCountsByType.getOrDefault(typeHourKey, 0);
        if (typeAlerts >= maxAlertsPerTypePerHour) {
            return true;
        }
        
        // Update counters
        hourlyAlertCounts.put(currentHour, totalAlerts + 1);
        hourlyAlertCountsByType.put(typeHourKey, typeAlerts + 1);
        
        return false;
    }

    private String getCurrentHourKey() {
        return String.valueOf(Instant.now().getEpochSecond() / 3600);
    }

    private void cleanupOldAlerts() {
        try {
            String currentHour = getCurrentHourKey();
            long currentHourLong = Long.parseLong(currentHour);
            
            // Remove alert counts older than 24 hours
            hourlyAlertCounts.entrySet().removeIf(entry -> {
                try {
                    long hour = Long.parseLong(entry.getKey());
                    return currentHourLong - hour > 24;
                } catch (NumberFormatException e) {
                    return true; // Remove invalid entries
                }
            });
            
            hourlyAlertCountsByType.entrySet().removeIf(entry -> {
                try {
                    String[] parts = entry.getKey().split(":");
                    if (parts.length != 2) return true;
                    long hour = Long.parseLong(parts[1]);
                    return currentHourLong - hour > 24;
                } catch (Exception e) {
                    return true; // Remove invalid entries
                }
            });
            
            // Remove old alert history (older than 7 days)
            Instant sevenDaysAgo = Instant.now().minus(java.time.Duration.ofDays(7));
            alertHistory.entrySet().removeIf(entry -> 
                entry.getValue().getFirstOccurrence().isBefore(sevenDaysAgo));
            
            log.debug("Alert cleanup completed");
            
        } catch (Exception e) {
            log.error("Failed to cleanup old alerts", e);
        }
    }

    // ============ ALERT HISTORY AND ESCALATION ============

    private void recordAlertHistory(LightningAlert alert) {
        String historyKey = alert.getAlertType();
        
        AlertHistory history = alertHistory.computeIfAbsent(historyKey, k -> 
            AlertHistory.builder()
                .alertType(k)
                .firstOccurrence(alert.getTimestamp())
                .lastOccurrence(alert.getTimestamp())
                .occurrenceCount(0)
                .lastSeverity(alert.getSeverity())
                .needsEscalation(false)
                .build()
        );
        
        history.setLastOccurrence(alert.getTimestamp());
        history.setOccurrenceCount(history.getOccurrenceCount() + 1);
        history.setLastSeverity(alert.getSeverity());
        
        // Mark for escalation if critical and recurring
        if (alert.getSeverity() == AlertSeverity.CRITICAL && 
            history.getOccurrenceCount() > 1) {
            history.setNeedsEscalation(true);
        }
    }

    private void scheduleEscalation(LightningAlert alert) {
        if (!escalationEnabled) {
            return;
        }
        
        int delayMinutes;
        switch (alert.getSeverity()) {
            case CRITICAL:
                delayMinutes = criticalAlertRepeatMinutes;
                break;
            case HIGH:
                delayMinutes = highAlertRepeatMinutes;
                break;
            default:
                return; // No escalation for lower severity alerts
        }
        
        alertExecutor.schedule(() -> {
            AlertHistory history = alertHistory.get(alert.getAlertType());
            if (history != null && history.isNeedsEscalation()) {
                escalateAlert(alert, history);
            }
        }, delayMinutes, TimeUnit.MINUTES);
    }

    private void processEscalations() {
        try {
            Instant now = Instant.now();
            
            alertHistory.values().forEach(history -> {
                if (history.isNeedsEscalation()) {
                    java.time.Duration timeSinceLastAlert = 
                        java.time.Duration.between(history.getLastOccurrence(), now);
                    
                    boolean shouldEscalate = false;
                    switch (history.getLastSeverity()) {
                        case CRITICAL:
                            shouldEscalate = timeSinceLastAlert.toMinutes() >= criticalAlertRepeatMinutes;
                            break;
                        case HIGH:
                            shouldEscalate = timeSinceLastAlert.toMinutes() >= highAlertRepeatMinutes;
                            break;
                    }
                    
                    if (shouldEscalate) {
                        LightningAlert escalationAlert = LightningAlert.builder()
                            .id(UUID.randomUUID().toString())
                            .severity(history.getLastSeverity())
                            .alertType(history.getAlertType() + "_ESCALATION")
                            .message(String.format("ESCALATION: %s has occurred %d times in the last %d minutes",
                                history.getAlertType(), 
                                history.getOccurrenceCount(),
                                java.time.Duration.between(history.getFirstOccurrence(), now).toMinutes()))
                            .timestamp(now)
                            .source("waqiti-crypto-service")
                            .environment(System.getProperty("spring.profiles.active", "development"))
                            .metadata(Map.of(
                                "original_alert_type", history.getAlertType(),
                                "occurrence_count", history.getOccurrenceCount(),
                                "first_occurrence", history.getFirstOccurrence().toString(),
                                "last_occurrence", history.getLastOccurrence().toString()
                            ))
                            .build();
                        
                        sendNotifications(escalationAlert);
                        history.setLastOccurrence(now);
                        
                        log.warn("Escalated alert: {} (occurred {} times)", 
                            history.getAlertType(), history.getOccurrenceCount());
                    }
                }
            });
            
        } catch (Exception e) {
            log.error("Failed to process alert escalations", e);
        }
    }

    private void escalateAlert(LightningAlert originalAlert, AlertHistory history) {
        // Send escalated alert to higher-level notification channels
        log.warn("Escalating alert: {} - occurred {} times", 
            originalAlert.getAlertType(), history.getOccurrenceCount());
        
        // In a real implementation, you might:
        // - Call additional phone numbers
        // - Send to different Slack channels or teams
        // - Create incidents in incident management systems
        // - Trigger automated remediation actions
    }

    // ============ UTILITY METHODS ============

    private AlertSeverity determineHealthAlertSeverity(LightningMonitoringService.HealthStatus status) {
        return switch (status) {
            case CRITICAL -> AlertSeverity.CRITICAL;
            case DEGRADED -> AlertSeverity.HIGH;
            case HEALTHY -> AlertSeverity.INFO;
            default -> AlertSeverity.MEDIUM;
        };
    }

    private String getSlackColor(AlertSeverity severity) {
        return switch (severity) {
            case CRITICAL -> "danger";
            case HIGH -> "#ff9900";
            case MEDIUM -> "warning";
            case LOW -> "#36a64f";
            case INFO -> "#17a2b8";
        };
    }

    private String getAlertEmoji(AlertSeverity severity) {
        return switch (severity) {
            case CRITICAL -> "🚨";
            case HIGH -> "⚠️";
            case MEDIUM -> "⚡";
            case LOW -> "ℹ️";
            case INFO -> "📊";
        };
    }

    private String getHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    // ============ PUBLIC MONITORING METHODS ============

    public Map<String, Object> getAlertStatistics() {
        String currentHour = getCurrentHourKey();
        
        return Map.of(
            "alerts_enabled", alertsEnabled,
            "current_hour_alerts", hourlyAlertCounts.getOrDefault(currentHour, 0),
            "max_alerts_per_hour", maxAlertsPerHour,
            "active_alert_types", alertHistory.size(),
            "escalation_enabled", escalationEnabled,
            "notification_channels", getEnabledNotificationChannels()
        );
    }

    public List<String> getEnabledNotificationChannels() {
        List<String> channels = new ArrayList<>();
        
        if (!slackWebhookUrl.isEmpty()) {
            channels.add("slack");
        }
        if (emailAlertsEnabled && !emailRecipients.isEmpty()) {
            channels.add("email");
        }
        if (!webhookUrls.isEmpty()) {
            channels.add("webhook");
        }
        if (smsAlertsEnabled && !smsRecipients.isEmpty()) {
            channels.add("sms");
        }
        
        return channels;
    }

    // ============ INNER CLASSES ============

    public enum AlertSeverity {
        CRITICAL, HIGH, MEDIUM, LOW, INFO
    }

    @lombok.Builder
    @lombok.Getter
    public static class LightningAlert {
        private final String id;
        private final AlertSeverity severity;
        private final String alertType;
        private final String message;
        private final Instant timestamp;
        private final String source;
        private final String environment;
        private final Map<String, Object> metadata;
    }

    @lombok.Builder
    @lombok.Getter
    @lombok.Setter
    public static class AlertHistory {
        private final String alertType;
        private final Instant firstOccurrence;
        private Instant lastOccurrence;
        private int occurrenceCount;
        private AlertSeverity lastSeverity;
        private boolean needsEscalation;
    }
}