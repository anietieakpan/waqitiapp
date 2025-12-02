package com.waqiti.common.monitoring;

import com.waqiti.common.kafka.ConsumerHealth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive alerting service for Kafka consumer monitoring
 * Supports multiple channels: Email, Slack, PagerDuty
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AlertingService {

    private final JavaMailSender mailSender;
    private final RestTemplate restTemplate;

    @Value("${alerting.email.enabled:true}")
    private boolean emailAlertsEnabled;

    @Value("${alerting.email.recipients:admin@example.com}")
    private String emailRecipients;

    @Value("${alerting.slack.enabled:false}")
    private boolean slackAlertsEnabled;

    @Value("${alerting.slack.webhook-url:}")
    private String slackWebhookUrl;

    @Value("${alerting.pagerduty.enabled:false}")
    private boolean pagerDutyEnabled;

    @Value("${alerting.pagerduty.integration-key:}")
    private String pagerDutyIntegrationKey;

    @Value("${alerting.throttle.enabled:true}")
    private boolean throttlingEnabled;

    @Value("${alerting.throttle.window-minutes:15}")
    private int throttleWindowMinutes;

    // Alert throttling to prevent spam
    private final Map<String, Instant> lastAlertTimes = new ConcurrentHashMap<>();

    /**
     * Send critical alert with type, message, and metadata (3-parameter signature)
     */
    public void sendCriticalAlert(String type, String message, Map<String, Object> metadata) {
        try {
            String alertKey = "critical-" + type;

            if (!shouldSendAlert(alertKey)) {
                return;
            }

            AlertMessage alert = AlertMessage.builder()
                .severity(AlertSeverity.CRITICAL)
                .title(type)
                .message(message)
                .source("critical-alert-system")
                .timestamp(Instant.now())
                .alertType(type)
                .metadata(metadata)
                .build();

            sendAlert(alert);
            recordAlertSent(alertKey);

        } catch (Exception e) {
            log.error("Failed to send critical alert: {}", type, e);
        }
    }

    /**
     * Send generic alert with type, message, and metadata (3-parameter signature)
     */
    public void sendAlert(String type, String message, Map<String, Object> metadata) {
        try {
            String alertKey = "alert-" + type;

            if (!shouldSendAlert(alertKey)) {
                return;
            }

            AlertMessage alert = AlertMessage.builder()
                .severity(AlertSeverity.WARNING)
                .title(type)
                .message(message)
                .source("alerting-system")
                .timestamp(Instant.now())
                .alertType(type)
                .metadata(metadata)
                .build();

            sendAlert(alert);
            recordAlertSent(alertKey);

        } catch (Exception e) {
            log.error("Failed to send alert: {}", type, e);
        }
    }

    /**
     * Send compliance alert with type, message, and metadata (3-parameter signature)
     */
    public void sendComplianceAlert(String type, String message, Map<String, Object> metadata) {
        try {
            String alertKey = "compliance-" + type;

            if (!shouldSendAlert(alertKey)) {
                return;
            }

            AlertMessage alert = AlertMessage.builder()
                .severity(AlertSeverity.CRITICAL)
                .title(type)
                .message(message)
                .source("compliance-alerting-system")
                .timestamp(Instant.now())
                .alertType(type)
                .metadata(metadata)
                .build();

            sendAlert(alert);
            recordAlertSent(alertKey);

        } catch (Exception e) {
            log.error("Failed to send compliance alert: {}", type, e);
        }
    }

    /**
     * Send DLQ alert
     */
    public void sendDlqAlert(String consumerName, String topic) {
        try {
            String alertKey = "dlq-" + consumerName + "-" + topic;
            
            if (!shouldSendAlert(alertKey)) {
                return;
            }

            AlertMessage alert = AlertMessage.builder()
                .severity(AlertSeverity.CRITICAL)
                .title("DLQ Alert: Message Processing Failed")
                .message(String.format("Consumer '%s' failed to process message from topic '%s'. Message sent to DLQ.", 
                                     consumerName, topic))
                .source("kafka-consumer-monitoring")
                .consumerName(consumerName)
                .topic(topic)
                .timestamp(Instant.now())
                .alertType("DLQ_MESSAGE")
                .build();

            sendAlert(alert);
            recordAlertSent(alertKey);
            
        } catch (Exception e) {
            log.error("Failed to send DLQ alert for consumer: {}, topic: {}", consumerName, topic, e);
        }
    }

    /**
     * Send high error rate alert
     */
    public void sendHighErrorRateAlert(String consumerName, String topic, double errorRate) {
        try {
            String alertKey = "error-rate-" + consumerName + "-" + topic;
            
            if (!shouldSendAlert(alertKey)) {
                return;
            }

            AlertSeverity severity = errorRate > 10.0 ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;

            AlertMessage alert = AlertMessage.builder()
                .severity(severity)
                .title("High Error Rate Alert")
                .message(String.format("Consumer '%s' on topic '%s' has high error rate: %.2f%%", 
                                     consumerName, topic, errorRate))
                .source("kafka-consumer-monitoring")
                .consumerName(consumerName)
                .topic(topic)
                .timestamp(Instant.now())
                .alertType("HIGH_ERROR_RATE")
                .metadata(Map.of("errorRate", errorRate))
                .build();

            sendAlert(alert);
            recordAlertSent(alertKey);
            
        } catch (Exception e) {
            log.error("Failed to send error rate alert for consumer: {}", consumerName, e);
        }
    }

    /**
     * Send consumer health alert
     */
    public void sendConsumerHealthAlert(String consumerName, ConsumerHealth health) {
        try {
            String alertKey = "health-" + consumerName;
            
            if (!shouldSendAlert(alertKey)) {
                return;
            }

            AlertSeverity severity = health.getStatus() == ConsumerHealth.HealthStatus.UNHEALTHY ? 
                AlertSeverity.CRITICAL : AlertSeverity.WARNING;

            AlertMessage alert = AlertMessage.builder()
                .severity(severity)
                .title("Consumer Health Alert")
                .message(String.format("Consumer '%s' health status: %s. %s", 
                                     consumerName, health.getStatus(), health.getHealthSummary()))
                .source("kafka-consumer-monitoring")
                .consumerName(consumerName)
                .timestamp(Instant.now())
                .alertType("CONSUMER_HEALTH")
                .metadata(Map.of(
                    "healthStatus", health.getStatus().toString(),
                    "lagMs", health.getLagMilliseconds() != null ? health.getLagMilliseconds() : 0,
                    "errorRate", health.getErrorRate() != null ? health.getErrorRate() : 0.0
                ))
                .build();

            sendAlert(alert);
            recordAlertSent(alertKey);
            
        } catch (Exception e) {
            log.error("Failed to send health alert for consumer: {}", consumerName, e);
        }
    }

    /**
     * Send consumer lag alert
     */
    public void sendConsumerLagAlert(String consumerName, long lagMs) {
        try {
            String alertKey = "lag-" + consumerName;
            
            if (!shouldSendAlert(alertKey)) {
                return;
            }

            AlertSeverity severity = lagMs > 600000 ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;

            AlertMessage alert = AlertMessage.builder()
                .severity(severity)
                .title("Consumer Lag Alert")
                .message(String.format("Consumer '%s' has high lag: %d seconds", 
                                     consumerName, lagMs / 1000))
                .source("kafka-consumer-monitoring")
                .consumerName(consumerName)
                .timestamp(Instant.now())
                .alertType("HIGH_LAG")
                .metadata(Map.of("lagMs", lagMs))
                .build();

            sendAlert(alert);
            recordAlertSent(alertKey);
            
        } catch (Exception e) {
            log.error("Failed to send lag alert for consumer: {}", consumerName, e);
        }
    }

    /**
     * Send stale heartbeat alert
     */
    public void sendConsumerStaleHeartbeatAlert(String consumerName, Instant lastHeartbeat) {
        try {
            String alertKey = "heartbeat-" + consumerName;
            
            if (!shouldSendAlert(alertKey)) {
                return;
            }

            long ageMinutes = (Instant.now().toEpochMilli() - lastHeartbeat.toEpochMilli()) / 60000;

            AlertMessage alert = AlertMessage.builder()
                .severity(AlertSeverity.CRITICAL)
                .title("Consumer Stale Heartbeat Alert")
                .message(String.format("Consumer '%s' has stale heartbeat. Last seen: %d minutes ago", 
                                     consumerName, ageMinutes))
                .source("kafka-consumer-monitoring")
                .consumerName(consumerName)
                .timestamp(Instant.now())
                .alertType("STALE_HEARTBEAT")
                .metadata(Map.of("lastHeartbeat", lastHeartbeat.toString(), "ageMinutes", ageMinutes))
                .build();

            sendAlert(alert);
            recordAlertSent(alertKey);
            
        } catch (Exception e) {
            log.error("Failed to send heartbeat alert for consumer: {}", consumerName, e);
        }
    }

    /**
     * Main alert sending method
     */
    private void sendAlert(AlertMessage alert) {
        log.info("Sending alert: {} - {}", alert.getTitle(), alert.getMessage());

        CompletableFuture.runAsync(() -> {
            try {
                if (emailAlertsEnabled) {
                    sendEmailAlert(alert);
                }
                
                if (slackAlertsEnabled && !slackWebhookUrl.isEmpty()) {
                    sendSlackAlert(alert);
                }
                
                if (pagerDutyEnabled && !pagerDutyIntegrationKey.isEmpty() && 
                    alert.getSeverity() == AlertSeverity.CRITICAL) {
                    sendPagerDutyAlert(alert);
                }
                
            } catch (Exception e) {
                log.error("Failed to send alert through channels", e);
            }
        });
    }

    private void sendEmailAlert(AlertMessage alert) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(emailRecipients.split(","));
            message.setSubject(String.format("[%s] %s", alert.getSeverity(), alert.getTitle()));
            message.setText(formatEmailBody(alert));
            message.setFrom("alerts@example.com");
            
            mailSender.send(message);
            log.debug("Email alert sent successfully");
            
        } catch (Exception e) {
            log.error("Failed to send email alert", e);
        }
    }

    private void sendSlackAlert(AlertMessage alert) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("text", alert.getTitle());
            payload.put("attachments", new Object[]{
                Map.of(
                    "color", getSlackColor(alert.getSeverity()),
                    "fields", new Object[]{
                        Map.of("title", "Message", "value", alert.getMessage(), "short", false),
                        Map.of("title", "Consumer", "value", alert.getConsumerName() != null ? alert.getConsumerName() : "N/A", "short", true),
                        Map.of("title", "Topic", "value", alert.getTopic() != null ? alert.getTopic() : "N/A", "short", true),
                        Map.of("title", "Time", "value", alert.getTimestamp().toString(), "short", true)
                    }
                )
            });
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            restTemplate.postForEntity(slackWebhookUrl, request, String.class);
            
            log.debug("Slack alert sent successfully");
            
        } catch (Exception e) {
            log.error("Failed to send Slack alert", e);
        }
    }

    private void sendPagerDutyAlert(AlertMessage alert) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("routing_key", pagerDutyIntegrationKey);
            payload.put("event_action", "trigger");
            payload.put("dedup_key", alert.getConsumerName() + "-" + alert.getAlertType());
            payload.put("payload", Map.of(
                "summary", alert.getTitle(),
                "severity", alert.getSeverity().toString().toLowerCase(),
                "source", alert.getSource(),
                "custom_details", Map.of(
                    "message", alert.getMessage(),
                    "consumer", alert.getConsumerName() != null ? alert.getConsumerName() : "unknown",
                    "topic", alert.getTopic() != null ? alert.getTopic() : "unknown"
                )
            ));
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            restTemplate.postForEntity("https://events.pagerduty.com/v2/enqueue", request, String.class);
            
            log.debug("PagerDuty alert sent successfully");
            
        } catch (Exception e) {
            log.error("Failed to send PagerDuty alert", e);
        }
    }

    private boolean shouldSendAlert(String alertKey) {
        if (!throttlingEnabled) {
            return true;
        }
        
        Instant lastAlert = lastAlertTimes.get(alertKey);
        if (lastAlert == null) {
            return true;
        }
        
        long minutesSinceLastAlert = (Instant.now().toEpochMilli() - lastAlert.toEpochMilli()) / 60000;
        return minutesSinceLastAlert >= throttleWindowMinutes;
    }

    private void recordAlertSent(String alertKey) {
        lastAlertTimes.put(alertKey, Instant.now());
        
        if (lastAlertTimes.size() > 1000) {
            Instant cutoff = Instant.now().minusSeconds(TimeUnit.HOURS.toSeconds(24));
            lastAlertTimes.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        }
    }

    private String formatEmailBody(AlertMessage alert) {
        StringBuilder body = new StringBuilder();
        body.append("Alert Details:\n");
        body.append("=============\n\n");
        body.append("Severity: ").append(alert.getSeverity()).append("\n");
        body.append("Time: ").append(alert.getTimestamp().toString()).append("\n");
        body.append("Source: ").append(alert.getSource()).append("\n");
        
        if (alert.getConsumerName() != null) {
            body.append("Consumer: ").append(alert.getConsumerName()).append("\n");
        }
        
        if (alert.getTopic() != null) {
            body.append("Topic: ").append(alert.getTopic()).append("\n");
        }
        
        body.append("\nMessage:\n");
        body.append(alert.getMessage()).append("\n");
        
        if (alert.getMetadata() != null && !alert.getMetadata().isEmpty()) {
            body.append("\nAdditional Information:\n");
            alert.getMetadata().forEach((key, value) -> 
                body.append("- ").append(key).append(": ").append(value).append("\n"));
        }
        
        body.append("\n---\n");
        body.append("This alert was generated by Waqiti Kafka Consumer Monitoring System\n");
        
        return body.toString();
    }

    private String getSlackColor(AlertSeverity severity) {
        switch (severity) {
            case CRITICAL: return "danger";
            case WARNING: return "warning";
            case INFO: return "good";
            default: return "#808080";
        }
    }

    public enum AlertSeverity {
        INFO,
        WARNING,
        CRITICAL
    }

    public void sendHighPriorityAlert(String title, String message, Map<String, String> metadata) {
        Map<String, Object> metadataObj = new HashMap<>(metadata);
        sendCriticalAlert("HIGH_PRIORITY", title + ": " + message, metadataObj);
    }

    public void sendMediumPriorityAlert(String title, String message, Map<String, String> metadata) {
        Map<String, Object> metadataObj = new HashMap<>(metadata);
        sendAlert("MEDIUM_PRIORITY", title + ": " + message, metadataObj);
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AlertMessage {
        private AlertSeverity severity;
        private String title;
        private String message;
        private String source;
        private String consumerName;
        private String topic;
        private Instant timestamp;
        private String alertType;
        private Map<String, Object> metadata;
    }
}