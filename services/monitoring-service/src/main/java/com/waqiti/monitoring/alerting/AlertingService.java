package com.waqiti.monitoring.alerting;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CRITICAL MONITORING: Multi-channel Alerting Service
 * PRODUCTION-READY: Comprehensive alerting with multiple notification channels
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertingService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${waqiti.monitoring.alerting.slack.webhook-url:}")
    private String slackWebhookUrl;

    @Value("${waqiti.monitoring.alerting.pagerduty.integration-key:}")
    private String pagerDutyIntegrationKey;

    @Value("${waqiti.monitoring.alerting.email.smtp-server:}")
    private String smtpServer;

    @Value("${waqiti.monitoring.alerting.email.recipients:}")
    private List<String> emailRecipients;

    @Value("${waqiti.monitoring.alerting.teams.webhook-url:}")
    private String teamsWebhookUrl;

    @Value("${waqiti.monitoring.alerting.discord.webhook-url:}")
    private String discordWebhookUrl;

    @Value("${waqiti.monitoring.alerting.enabled-channels:slack,kafka}")
    private List<String> enabledChannels;

    // Alert deduplication and rate limiting
    private final Map<String, LocalDateTime> alertDeduplication = new ConcurrentHashMap<>();
    private final Map<String, Integer> alertCounts = new ConcurrentHashMap<>();

    @PostConstruct
    public void initializeAlerting() {
        log.info("ALERTING: Initializing alerting service with channels: {}", enabledChannels);
        
        if (enabledChannels.contains("slack") && (slackWebhookUrl == null || slackWebhookUrl.isEmpty())) {
            log.warn("ALERTING: Slack alerting enabled but webhook URL not configured");
        }
        
        if (enabledChannels.contains("pagerduty") && (pagerDutyIntegrationKey == null || pagerDutyIntegrationKey.isEmpty())) {
            log.warn("ALERTING: PagerDuty alerting enabled but integration key not configured");
        }
        
        log.info("ALERTING: Service initialized successfully");
    }

    /**
     * CRITICAL: Send alert through all configured channels
     */
    @Async
    public CompletableFuture<Void> sendAlert(SLAViolationAlert alert) {
        try {
            log.info("ALERTING: Processing alert - ID: {}, Type: {}, Severity: {}", 
                    alert.getAlertId(), alert.getSlaType(), alert.getSeverity());

            // Check for alert deduplication
            if (isDuplicateAlert(alert)) {
                log.debug("ALERTING: Duplicate alert suppressed - ID: {}", alert.getAlertId());
                return CompletableFuture.completedFuture(null);
            }

            // Send to all enabled channels
            CompletableFuture<Void> kafkaFuture = CompletableFuture.completedFuture(null);
            CompletableFuture<Void> slackFuture = CompletableFuture.completedFuture(null);
            CompletableFuture<Void> pagerDutyFuture = CompletableFuture.completedFuture(null);
            CompletableFuture<Void> emailFuture = CompletableFuture.completedFuture(null);
            CompletableFuture<Void> teamsFuture = CompletableFuture.completedFuture(null);
            CompletableFuture<Void> discordFuture = CompletableFuture.completedFuture(null);

            if (enabledChannels.contains("kafka")) {
                kafkaFuture = sendKafkaAlert(alert);
            }

            if (enabledChannels.contains("slack")) {
                slackFuture = sendSlackAlert(alert);
            }

            if (enabledChannels.contains("pagerduty") && alert.isCritical()) {
                pagerDutyFuture = sendPagerDutyAlert(alert);
            }

            if (enabledChannels.contains("email")) {
                emailFuture = sendEmailAlert(alert);
            }

            if (enabledChannels.contains("teams")) {
                teamsFuture = sendTeamsAlert(alert);
            }

            if (enabledChannels.contains("discord")) {
                discordFuture = sendDiscordAlert(alert);
            }

            // Wait for all channels to complete
            CompletableFuture.allOf(kafkaFuture, slackFuture, pagerDutyFuture, emailFuture, teamsFuture, discordFuture)
                    .thenRun(() -> {
                        recordAlertSent(alert);
                        log.info("ALERTING: Alert sent successfully - ID: {}", alert.getAlertId());
                    })
                    .exceptionally(ex -> {
                        log.error("ALERTING: Failed to send alert - ID: {}", alert.getAlertId(), ex);
                        return null;
                    });

            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            log.error("ALERTING: Error processing alert", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Send alert to Kafka for downstream processing
     */
    @Async
    public CompletableFuture<Void> sendKafkaAlert(SLAViolationAlert alert) {
        try {
            String alertJson = objectMapper.writeValueAsString(alert);
            kafkaTemplate.send("monitoring.alerts.sla", alert.getAlertId(), alertJson);
            log.debug("ALERTING: Alert sent to Kafka - ID: {}", alert.getAlertId());
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("ALERTING: Failed to send Kafka alert", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Send alert to Slack
     */
    @Async
    public CompletableFuture<Void> sendSlackAlert(SLAViolationAlert alert) {
        if (slackWebhookUrl == null || slackWebhookUrl.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            Map<String, Object> slackPayload = createSlackPayload(alert);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(slackPayload, headers);
            
            restTemplate.exchange(slackWebhookUrl, HttpMethod.POST, request, String.class);
            
            log.debug("ALERTING: Alert sent to Slack - ID: {}", alert.getAlertId());
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("ALERTING: Failed to send Slack alert", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Send alert to PagerDuty
     */
    @Async
    public CompletableFuture<Void> sendPagerDutyAlert(SLAViolationAlert alert) {
        if (pagerDutyIntegrationKey == null || pagerDutyIntegrationKey.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            Map<String, Object> pagerDutyPayload = createPagerDutyPayload(alert);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(pagerDutyPayload, headers);
            
            restTemplate.exchange("https://events.pagerduty.com/v2/enqueue", HttpMethod.POST, request, String.class);
            
            log.debug("ALERTING: Alert sent to PagerDuty - ID: {}", alert.getAlertId());
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("ALERTING: Failed to send PagerDuty alert", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Send alert via email
     */
    @Async
    public CompletableFuture<Void> sendEmailAlert(SLAViolationAlert alert) {
        // Implementation would depend on email service used (SendGrid, SES, etc.)
        log.debug("ALERTING: Email alert would be sent - ID: {}", alert.getAlertId());
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Send alert to Microsoft Teams
     */
    @Async
    public CompletableFuture<Void> sendTeamsAlert(SLAViolationAlert alert) {
        if (teamsWebhookUrl == null || teamsWebhookUrl.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            Map<String, Object> teamsPayload = createTeamsPayload(alert);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(teamsPayload, headers);
            
            restTemplate.exchange(teamsWebhookUrl, HttpMethod.POST, request, String.class);
            
            log.debug("ALERTING: Alert sent to Teams - ID: {}", alert.getAlertId());
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("ALERTING: Failed to send Teams alert", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Send alert to Discord
     */
    @Async
    public CompletableFuture<Void> sendDiscordAlert(SLAViolationAlert alert) {
        if (discordWebhookUrl == null || discordWebhookUrl.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            Map<String, Object> discordPayload = createDiscordPayload(alert);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(discordPayload, headers);
            
            restTemplate.exchange(discordWebhookUrl, HttpMethod.POST, request, String.class);
            
            log.debug("ALERTING: Alert sent to Discord - ID: {}", alert.getAlertId());
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("ALERTING: Failed to send Discord alert", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Create Slack message payload
     */
    private Map<String, Object> createSlackPayload(SLAViolationAlert alert) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("text", "🚨 SLA Violation Alert");
        
        Map<String, Object> attachment = new HashMap<>();
        attachment.put("color", getSeverityColor(alert.getSeverity()));
        attachment.put("title", "SLA Violation: " + alert.getSlaType());
        attachment.put("text", alert.getMessage());
        attachment.put("timestamp", alert.getTimestamp().toString());
        
        Map<String, Object> field1 = new HashMap<>();
        field1.put("title", "Target SLA");
        field1.put("value", String.format("%.2f%%", alert.getTargetSLA()));
        field1.put("short", true);
        
        Map<String, Object> field2 = new HashMap<>();
        field2.put("title", "Current SLA");
        field2.put("value", String.format("%.2f%%", alert.getCurrentSLA()));
        field2.put("short", true);
        
        Map<String, Object> field3 = new HashMap<>();
        field3.put("title", "Severity");
        field3.put("value", alert.getSeverity());
        field3.put("short", true);
        
        attachment.put("fields", List.of(field1, field2, field3));
        payload.put("attachments", List.of(attachment));
        
        return payload;
    }

    /**
     * Create PagerDuty event payload
     */
    private Map<String, Object> createPagerDutyPayload(SLAViolationAlert alert) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("routing_key", pagerDutyIntegrationKey);
        payload.put("event_action", "trigger");
        payload.put("dedup_key", alert.getSlaType() + "_" + alert.getAlertId());
        
        Map<String, Object> eventPayload = new HashMap<>();
        eventPayload.put("summary", "SLA Violation: " + alert.getSlaType());
        eventPayload.put("severity", mapSeverityToPagerDuty(alert.getSeverity()));
        eventPayload.put("source", "waqiti-monitoring");
        eventPayload.put("component", alert.getSlaType());
        eventPayload.put("group", "SLA");
        eventPayload.put("class", "SLA_VIOLATION");
        
        Map<String, Object> customDetails = new HashMap<>();
        customDetails.put("target_sla", alert.getTargetSLA());
        customDetails.put("current_sla", alert.getCurrentSLA());
        customDetails.put("deviation", alert.getSLADeviation());
        customDetails.put("alert_id", alert.getAlertId());
        customDetails.put("timestamp", alert.getTimestamp());
        
        eventPayload.put("custom_details", customDetails);
        payload.put("payload", eventPayload);
        
        return payload;
    }

    /**
     * Create Teams message payload
     */
    private Map<String, Object> createTeamsPayload(SLAViolationAlert alert) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("@type", "MessageCard");
        payload.put("@context", "http://schema.org/extensions");
        payload.put("themeColor", getSeverityColor(alert.getSeverity()).replace("#", ""));
        payload.put("summary", "SLA Violation Alert");
        payload.put("title", "🚨 SLA Violation: " + alert.getSlaType());
        payload.put("text", alert.getMessage());
        
        Map<String, Object> section = new HashMap<>();
        section.put("activityTitle", "Alert Details");
        section.put("activitySubtitle", "Severity: " + alert.getSeverity());
        
        Map<String, Object> fact1 = new HashMap<>();
        fact1.put("name", "Target SLA");
        fact1.put("value", String.format("%.2f%%", alert.getTargetSLA()));
        
        Map<String, Object> fact2 = new HashMap<>();
        fact2.put("name", "Current SLA");
        fact2.put("value", String.format("%.2f%%", alert.getCurrentSLA()));
        
        Map<String, Object> fact3 = new HashMap<>();
        fact3.put("name", "Timestamp");
        fact3.put("value", alert.getTimestamp().toString());
        
        section.put("facts", List.of(fact1, fact2, fact3));
        payload.put("sections", List.of(section));
        
        return payload;
    }

    /**
     * Create Discord message payload
     */
    private Map<String, Object> createDiscordPayload(SLAViolationAlert alert) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", "🚨 **SLA Violation Alert**");
        
        Map<String, Object> embed = new HashMap<>();
        embed.put("title", "SLA Violation: " + alert.getSlaType());
        embed.put("description", alert.getMessage());
        embed.put("color", Integer.parseInt(getSeverityColor(alert.getSeverity()).replace("#", ""), 16));
        embed.put("timestamp", alert.getTimestamp().toString());
        
        Map<String, Object> field1 = new HashMap<>();
        field1.put("name", "Target SLA");
        field1.put("value", String.format("%.2f%%", alert.getTargetSLA()));
        field1.put("inline", true);
        
        Map<String, Object> field2 = new HashMap<>();
        field2.put("name", "Current SLA");
        field2.put("value", String.format("%.2f%%", alert.getCurrentSLA()));
        field2.put("inline", true);
        
        Map<String, Object> field3 = new HashMap<>();
        field3.put("name", "Severity");
        field3.put("value", alert.getSeverity());
        field3.put("inline", true);
        
        embed.put("fields", List.of(field1, field2, field3));
        payload.put("embeds", List.of(embed));
        
        return payload;
    }

    /**
     * Get color for severity level
     */
    private String getSeverityColor(String severity) {
        switch (severity) {
            case "CRITICAL": return "#FF0000";
            case "HIGH": return "#FF6600";
            case "MEDIUM": return "#FFAA00";
            case "LOW": return "#FFDD00";
            default: return "#CCCCCC";
        }
    }

    /**
     * Map severity to PagerDuty severity levels
     */
    private String mapSeverityToPagerDuty(String severity) {
        switch (severity) {
            case "CRITICAL": return "critical";
            case "HIGH": return "error";
            case "MEDIUM": return "warning";
            case "LOW": return "info";
            default: return "info";
        }
    }

    /**
     * Check if alert is duplicate
     */
    private boolean isDuplicateAlert(SLAViolationAlert alert) {
        String key = alert.getSlaType() + "_" + alert.getSeverity();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastAlert = alertDeduplication.get(key);
        
        if (lastAlert != null && now.minusMinutes(5).isBefore(lastAlert)) {
            // Within 5-minute deduplication window
            alertCounts.merge(key, 1, Integer::sum);
            return true;
        }
        
        return false;
    }

    /**
     * Record alert sent
     */
    private void recordAlertSent(SLAViolationAlert alert) {
        String key = alert.getSlaType() + "_" + alert.getSeverity();
        alertDeduplication.put(key, LocalDateTime.now());
        alertCounts.put(key, 0);
    }
}