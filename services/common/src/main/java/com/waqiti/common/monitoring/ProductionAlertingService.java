package com.waqiti.common.monitoring;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Production Alerting Service
 * Handles critical system alerts, notifications, and incident management
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductionAlertingService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RestTemplate restTemplate;

    @Value("${alerting.enabled:true}")
    private boolean alertingEnabled;

    @Value("${alerting.slack.webhook-url:}")
    private String slackWebhookUrl;

    @Value("${alerting.pagerduty.integration-key:}")
    private String pagerDutyIntegrationKey;

    @Value("${alerting.email.smtp.enabled:false}")
    private boolean emailAlertsEnabled;

    @Value("${alerting.teams.webhook-url:}")
    private String teamsWebhookUrl;

    @Value("${alerting.critical.cooldown-minutes:10}")
    private int criticalAlertCooldownMinutes;

    @Value("${alerting.warning.cooldown-minutes:5}")
    private int warningAlertCooldownMinutes;

    @Value("${spring.application.name:unknown}")
    private String serviceName;

    @Value("${spring.profiles.active:unknown}")
    private String environment;

    // Alert suppression to prevent spam
    private final Map<String, Instant> alertSuppressionMap = new ConcurrentHashMap<>();
    
    // Executor for async alert processing
    private final ScheduledExecutorService alertExecutor = Executors.newScheduledThreadPool(3);

    /**
     * Send critical alert that requires immediate attention
     */
    public void sendCriticalAlert(String title, String description, Map<String, Object> metadata) {
        if (!alertingEnabled) {
            log.debug("Alerting disabled, skipping critical alert: {}", title);
            return;
        }

        Alert alert = Alert.builder()
            .id(UUID.randomUUID().toString())
            .severity(AlertSeverity.CRITICAL)
            .title(title)
            .description(description)
            .source(serviceName)
            .environment(environment)
            .timestamp(Instant.now())
            .metadata(metadata != null ? metadata : new HashMap<>())
            .build();

        processAlert(alert);
    }

    /**
     * Send warning alert for issues that need attention but aren't critical
     */
    public void sendWarningAlert(String title, String description, Map<String, Object> metadata) {
        if (!alertingEnabled) {
            log.debug("Alerting disabled, skipping warning alert: {}", title);
            return;
        }

        Alert alert = Alert.builder()
            .id(UUID.randomUUID().toString())
            .severity(AlertSeverity.WARNING)
            .title(title)
            .description(description)
            .source(serviceName)
            .environment(environment)
            .timestamp(Instant.now())
            .metadata(metadata != null ? metadata : new HashMap<>())
            .build();

        processAlert(alert);
    }

    /**
     * Send info alert for general notifications
     */
    public void sendInfoAlert(String title, String description, Map<String, Object> metadata) {
        if (!alertingEnabled) {
            log.debug("Alerting disabled, skipping info alert: {}", title);
            return;
        }

        Alert alert = Alert.builder()
            .id(UUID.randomUUID().toString())
            .severity(AlertSeverity.INFO)
            .title(title)
            .description(description)
            .source(serviceName)
            .environment(environment)
            .timestamp(Instant.now())
            .metadata(metadata != null ? metadata : new HashMap<>())
            .build();

        processAlert(alert);
    }

    /**
     * Send security alert for security-related incidents
     */
    public void sendSecurityAlert(String title, String description, String threatLevel, 
                                 Map<String, Object> securityMetadata) {
        Map<String, Object> metadata = new HashMap<>(securityMetadata);
        metadata.put("threatLevel", threatLevel);
        metadata.put("category", "SECURITY");

        Alert alert = Alert.builder()
            .id(UUID.randomUUID().toString())
            .severity(AlertSeverity.CRITICAL)
            .title("[SECURITY] " + title)
            .description(description)
            .source(serviceName)
            .environment(environment)
            .timestamp(Instant.now())
            .metadata(metadata)
            .build();

        // Security alerts bypass cooldown
        processAlert(alert, true);
    }

    /**
     * Send performance alert when system performance degrades
     */
    public void sendPerformanceAlert(String metric, double currentValue, double threshold, 
                                   String unit, Map<String, Object> perfMetadata) {
        Map<String, Object> metadata = new HashMap<>(perfMetadata);
        metadata.put("metric", metric);
        metadata.put("currentValue", currentValue);
        metadata.put("threshold", threshold);
        metadata.put("unit", unit);
        metadata.put("category", "PERFORMANCE");

        String title = String.format("Performance Alert: %s", metric);
        String description = String.format("%s is %s%s (threshold: %s%s)", 
            metric, currentValue, unit, threshold, unit);

        Alert alert = Alert.builder()
            .id(UUID.randomUUID().toString())
            .severity(currentValue > threshold * 1.5 ? AlertSeverity.CRITICAL : AlertSeverity.WARNING)
            .title(title)
            .description(description)
            .source(serviceName)
            .environment(environment)
            .timestamp(Instant.now())
            .metadata(metadata)
            .build();

        processAlert(alert);
    }

    /**
     * Send business metric alert for business-critical KPIs
     */
    public void sendBusinessAlert(String metric, double currentValue, double threshold,
                                 AlertSeverity severity, Map<String, Object> businessMetadata) {
        Map<String, Object> metadata = new HashMap<>(businessMetadata);
        metadata.put("metric", metric);
        metadata.put("currentValue", currentValue);
        metadata.put("threshold", threshold);
        metadata.put("category", "BUSINESS");

        String title = String.format("Business Alert: %s", metric);
        String description = String.format("Business metric %s: %s (threshold: %s)", 
            metric, currentValue, threshold);

        Alert alert = Alert.builder()
            .id(UUID.randomUUID().toString())
            .severity(severity)
            .title(title)
            .description(description)
            .source(serviceName)
            .environment(environment)
            .timestamp(Instant.now())
            .metadata(metadata)
            .build();

        processAlert(alert);
    }

    /**
     * Process alert through all configured channels
     */
    private void processAlert(Alert alert) {
        processAlert(alert, false);
    }

    private void processAlert(Alert alert, boolean bypassCooldown) {
        // Check alert suppression unless bypassed
        if (!bypassCooldown && isAlertSuppressed(alert)) {
            log.debug("Alert suppressed due to cooldown: {}", alert.getTitle());
            return;
        }

        // Store alert in Redis for tracking
        storeAlert(alert);

        // Send to Kafka for processing
        sendToKafka(alert);

        // Send through configured channels asynchronously
        alertExecutor.submit(() -> {
            try {
                if (!slackWebhookUrl.isEmpty()) {
                    sendSlackAlert(alert);
                }

                if (!teamsWebhookUrl.isEmpty()) {
                    sendTeamsAlert(alert);
                }

                if (!pagerDutyIntegrationKey.isEmpty() && 
                    (alert.getSeverity() == AlertSeverity.CRITICAL)) {
                    sendPagerDutyAlert(alert);
                }

                if (emailAlertsEnabled) {
                    sendEmailAlert(alert);
                }

                // Update suppression map
                updateAlertSuppression(alert);

                log.info("Alert processed successfully: {} - {}", alert.getSeverity(), alert.getTitle());

            } catch (Exception e) {
                log.error("Failed to process alert: {}", alert.getTitle(), e);
            }
        });
    }

    /**
     * Check if alert should be suppressed due to cooldown
     */
    private boolean isAlertSuppressed(Alert alert) {
        String suppressionKey = generateSuppressionKey(alert);
        Instant lastSent = alertSuppressionMap.get(suppressionKey);
        
        if (lastSent == null) {
            return false;
        }

        Duration cooldown = switch (alert.getSeverity()) {
            case CRITICAL -> Duration.ofMinutes(criticalAlertCooldownMinutes);
            case WARNING -> Duration.ofMinutes(warningAlertCooldownMinutes);
            case INFO -> Duration.ofMinutes(warningAlertCooldownMinutes * 2);
        };

        return Instant.now().isBefore(lastSent.plus(cooldown));
    }

    /**
     * Store alert in Redis for tracking and analytics
     */
    private void storeAlert(Alert alert) {
        try {
            String alertKey = "alerts:" + alert.getId();
            redisTemplate.opsForValue().set(alertKey, alert, Duration.ofDays(30));

            // Add to alerts index
            String indexKey = "alerts:index:" + alert.getSeverity().name().toLowerCase();
            redisTemplate.opsForList().leftPush(indexKey, alert.getId());
            redisTemplate.opsForList().trim(indexKey, 0, 999); // Keep last 1000 alerts
            redisTemplate.expire(indexKey, Duration.ofDays(30));

        } catch (Exception e) {
            log.error("Failed to store alert in Redis", e);
        }
    }

    /**
     * Send alert to Kafka for downstream processing
     */
    private void sendToKafka(Alert alert) {
        try {
            kafkaTemplate.send("alerts", alert.getId(), alert);
        } catch (Exception e) {
            log.error("Failed to send alert to Kafka", e);
        }
    }

    /**
     * Send alert to Slack
     */
    private void sendSlackAlert(Alert alert) {
        try {
            SlackMessage message = SlackMessage.builder()
                .text(formatSlackMessage(alert))
                .username("WaqitiAlerts")
                .iconEmoji(getAlertEmoji(alert.getSeverity()))
                .build();

            restTemplate.postForEntity(slackWebhookUrl, message, String.class);
            log.debug("Slack alert sent: {}", alert.getTitle());

        } catch (Exception e) {
            log.error("Failed to send Slack alert", e);
        }
    }

    /**
     * Send alert to Microsoft Teams
     */
    private void sendTeamsAlert(Alert alert) {
        try {
            TeamsMessage message = TeamsMessage.builder()
                .title(alert.getTitle())
                .text(formatTeamsMessage(alert))
                .themeColor(getAlertColor(alert.getSeverity()))
                .build();

            restTemplate.postForEntity(teamsWebhookUrl, message, String.class);
            log.debug("Teams alert sent: {}", alert.getTitle());

        } catch (Exception e) {
            log.error("Failed to send Teams alert", e);
        }
    }

    /**
     * Send alert to PagerDuty
     */
    private void sendPagerDutyAlert(Alert alert) {
        try {
            PagerDutyEvent event = PagerDutyEvent.builder()
                .routingKey(pagerDutyIntegrationKey)
                .eventAction("trigger")
                .payload(PagerDutyPayload.builder()
                    .summary(alert.getTitle())
                    .source(alert.getSource())
                    .severity(alert.getSeverity().name().toLowerCase())
                    .customDetails(alert.getMetadata())
                    .build())
                .build();

            restTemplate.postForEntity("https://events.pagerduty.com/v2/enqueue", event, String.class);
            log.debug("PagerDuty alert sent: {}", alert.getTitle());

        } catch (Exception e) {
            log.error("Failed to send PagerDuty alert", e);
        }
    }

    /**
     * Send email alert (placeholder - would integrate with email service)
     */
    private void sendEmailAlert(Alert alert) {
        try {
            // This would integrate with an email service like SendGrid, SES, etc.
            log.info("Email alert would be sent: {}", alert.getTitle());
        } catch (Exception e) {
            log.error("Failed to send email alert", e);
        }
    }

    /**
     * Update alert suppression tracking
     */
    private void updateAlertSuppression(Alert alert) {
        String suppressionKey = generateSuppressionKey(alert);
        alertSuppressionMap.put(suppressionKey, Instant.now());
        
        // Clean up old suppression entries
        alertExecutor.schedule(this::cleanupSuppressionMap, 1, TimeUnit.HOURS);
    }

    /**
     * Generate suppression key for alert deduplication
     */
    private String generateSuppressionKey(Alert alert) {
        return String.format("%s:%s:%s", 
            alert.getSource(), alert.getSeverity(), alert.getTitle().hashCode());
    }

    /**
     * Clean up old suppression entries
     */
    private void cleanupSuppressionMap() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(2));
        alertSuppressionMap.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    // Message formatting methods

    private String formatSlackMessage(Alert alert) {
        StringBuilder sb = new StringBuilder();
        sb.append("*").append(alert.getSeverity()).append(" Alert*\n");
        sb.append("*Service:* ").append(alert.getSource()).append("\n");
        sb.append("*Environment:* ").append(alert.getEnvironment()).append("\n");
        sb.append("*Description:* ").append(alert.getDescription()).append("\n");
        sb.append("*Time:* ").append(alert.getTimestamp()).append("\n");
        
        if (!alert.getMetadata().isEmpty()) {
            sb.append("*Additional Info:*\n");
            alert.getMetadata().forEach((k, v) -> 
                sb.append("• ").append(k).append(": ").append(v).append("\n"));
        }
        
        return sb.toString();
    }

    private String formatTeamsMessage(Alert alert) {
        StringBuilder sb = new StringBuilder();
        sb.append("**Service:** ").append(alert.getSource()).append("\n\n");
        sb.append("**Environment:** ").append(alert.getEnvironment()).append("\n\n");
        sb.append("**Description:** ").append(alert.getDescription()).append("\n\n");
        sb.append("**Time:** ").append(alert.getTimestamp()).append("\n\n");
        
        if (!alert.getMetadata().isEmpty()) {
            sb.append("**Additional Information:**\n\n");
            alert.getMetadata().forEach((k, v) -> 
                sb.append("- ").append(k).append(": ").append(v).append("\n\n"));
        }
        
        return sb.toString();
    }

    private String getAlertEmoji(AlertSeverity severity) {
        return switch (severity) {
            case CRITICAL -> ":rotating_light:";
            case WARNING -> ":warning:";
            case INFO -> ":information_source:";
        };
    }

    private String getAlertColor(AlertSeverity severity) {
        return switch (severity) {
            case CRITICAL -> "FF0000"; // Red
            case WARNING -> "FFA500";  // Orange
            case INFO -> "0078D4";     // Blue
        };
    }

    /**
     * Shutdown alert executor on application shutdown
     */
    @jakarta.annotation.PreDestroy
    public void shutdown() {
        log.info("Shutting down alerting service");
        alertExecutor.shutdown();
        try {
            if (!alertExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                alertExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            alertExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // Data classes

    @Data
    @Builder
    public static class Alert {
        private String id;
        private AlertSeverity severity;
        private String title;
        private String description;
        private String source;
        private String environment;
        private Instant timestamp;
        private Map<String, Object> metadata;
    }

    public enum AlertSeverity {
        CRITICAL, WARNING, INFO
    }

    @Data
    @Builder
    public static class SlackMessage {
        private String text;
        private String username;
        private String iconEmoji;
    }

    @Data
    @Builder
    public static class TeamsMessage {
        private String title;
        private String text;
        private String themeColor;
    }

    @Data
    @Builder
    public static class PagerDutyEvent {
        private String routingKey;
        private String eventAction;
        private PagerDutyPayload payload;
    }

    @Data
    @Builder
    public static class PagerDutyPayload {
        private String summary;
        private String source;
        private String severity;
        private Map<String, Object> customDetails;
    }
}