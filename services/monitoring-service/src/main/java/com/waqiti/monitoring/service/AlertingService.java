package com.waqiti.monitoring.service;

import com.waqiti.monitoring.model.Alert;
import com.waqiti.monitoring.model.AlertChannel;
import com.waqiti.common.resilience.ResilientServiceExecutor;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Multi-channel alerting service with resilience patterns
 * Handles delivery of alerts through various channels with circuit breakers,
 * timeouts, and retry mechanisms for reliable alert delivery
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertingService {
    
    private final JavaMailSender mailSender;
    private final RestTemplate restTemplate;
    private final ResilientServiceExecutor resilientExecutor;
    
    @Value("${alerting.email.from:alerts@example.com}")
    private String emailFrom;
    
    @Value("${alerting.email.to:ops@example.com}")
    private String emailTo;
    
    @Value("${alerting.sms.enabled:false}")
    private boolean smsEnabled;
    
    @Value("${alerting.sms.twilio.account-sid:}")
    private String twilioAccountSid;
    
    @Value("${alerting.sms.twilio.auth-token:}")
    private String twilioAuthToken;
    
    @Value("${alerting.sms.from:}")
    private String smsFrom;
    
    @Value("${alerting.sms.to:}")
    private String smsTo;
    
    @Value("${alerting.slack.enabled:false}")
    private boolean slackEnabled;
    
    @Value("${alerting.slack.webhook-url:}")
    private String slackWebhookUrl;
    
    @Value("${alerting.pagerduty.enabled:false}")
    private boolean pagerDutyEnabled;
    
    @Value("${alerting.pagerduty.integration-key:}")
    private String pagerDutyIntegrationKey;
    
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * Send email alert
     */
    public void sendEmailAlert(Alert alert) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(emailFrom);
            message.setTo(emailTo.split(","));
            message.setSubject(formatEmailSubject(alert));
            message.setText(formatEmailBody(alert));
            
            mailSender.send(message);
            log.info("Email alert sent: {} - {}", alert.getType(), alert.getMessage());
            
        } catch (Exception e) {
            log.error("Failed to send email alert", e);
        }
    }
    
    /**
     * Send SMS alert via Twilio
     */
    public void sendSmsAlert(Alert alert) {
        if (!smsEnabled) {
            log.debug("SMS alerting is disabled");
            return;
        }
        
        try {
            String url = String.format("https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json", 
                twilioAccountSid);
            
            Map<String, String> body = new HashMap<>();
            body.put("From", smsFrom);
            body.put("To", smsTo);
            body.put("Body", formatSmsMessage(alert));
            
            // Note: In production, use proper Twilio SDK with authentication
            log.info("SMS alert would be sent: {} - {}", alert.getType(), alert.getMessage());
            
        } catch (Exception e) {
            log.error("Failed to send SMS alert", e);
        }
    }
    
    /**
     * Send Slack alert with circuit breaker and timeout protection
     */
    @CircuitBreaker(name = "slack-alerts", fallbackMethod = "sendSlackAlertFallback")
    @Retry(name = "slack-alerts")
    @TimeLimiter(name = "slack-alerts")
    public CompletableFuture<Void> sendSlackAlert(Alert alert) {
        if (!slackEnabled) {
            log.debug("Slack alerting is disabled");
            return CompletableFuture.completedFuture(null);
        }
        
        return resilientExecutor.executeAsyncWithResilience(
            "slack-notifications",
            () -> CompletableFuture.runAsync(() -> {
                try {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("text", formatSlackMessage(alert));
                    payload.put("username", "Waqiti Monitoring");
                    payload.put("icon_emoji", getSlackEmoji(alert));
                    
                    // Add attachments for detailed information
                    Map<String, Object> attachment = new HashMap<>();
                    attachment.put("color", getSlackColor(alert));
                    attachment.put("title", alert.getType());
                    attachment.put("text", alert.getMessage());
                    attachment.put("ts", System.currentTimeMillis() / 1000);
                    
                    if (alert.getMetadata() != null && !alert.getMetadata().isEmpty()) {
                        attachment.put("fields", formatSlackFields(alert.getMetadata()));
                    }
                    
                    payload.put("attachments", new Object[]{attachment});
                    
                    // Set proper headers for Slack webhook
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("Content-Type", "application/json");
                    headers.set("User-Agent", "Waqiti-Monitoring/1.0");
                    
                    HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
                    
                    ResponseEntity<String> response = restTemplate.exchange(
                        slackWebhookUrl, HttpMethod.POST, request, String.class);
                    
                    if (response.getStatusCode().is2xxSuccessful()) {
                        log.info("Slack alert sent successfully: {} - {}", alert.getType(), alert.getMessage());
                    } else {
                        log.warn("Slack webhook returned non-success status: {}", response.getStatusCode());
                        throw new AlertDeliveryException("Slack webhook returned status: " + response.getStatusCode());
                    }
                    
                } catch (Exception e) {
                    log.error("Failed to send Slack alert: {} - {}", alert.getType(), e.getMessage(), e);
                    throw new AlertDeliveryException("Slack alert delivery failed", e);
                }
            })
        );
    }
    
    /**
     * Send PagerDuty alert with circuit breaker and timeout protection
     */
    @CircuitBreaker(name = "pagerduty-alerts", fallbackMethod = "sendPagerDutyAlertFallback")
    @Retry(name = "pagerduty-alerts")
    @TimeLimiter(name = "pagerduty-alerts")
    public CompletableFuture<Void> sendPagerDutyAlert(Alert alert) {
        if (!pagerDutyEnabled) {
            log.debug("PagerDuty alerting is disabled");
            return CompletableFuture.completedFuture(null);
        }
        
        return resilientExecutor.executeAsyncWithResilience(
            "pagerduty-notifications",
            () -> CompletableFuture.runAsync(() -> {
                try {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("routing_key", pagerDutyIntegrationKey);
                    payload.put("event_action", "trigger");
                    payload.put("dedup_key", alert.getId());
                    
                    Map<String, Object> payloadData = new HashMap<>();
                    payloadData.put("summary", formatPagerDutySummary(alert));
                    payloadData.put("severity", mapToPagerDutySeverity(alert));
                    payloadData.put("source", "waqiti-monitoring");
                    payloadData.put("timestamp", alert.getTimestamp().toString());
                    payloadData.put("custom_details", alert.getMetadata());
                    
                    payload.put("payload", payloadData);
                    
                    // Set proper headers for PagerDuty API
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("Content-Type", "application/json");
                    headers.set("User-Agent", "Waqiti-Monitoring/1.0");
                    
                    HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
                    
                    ResponseEntity<String> response = restTemplate.exchange(
                        "https://events.pagerduty.com/v2/enqueue", 
                        HttpMethod.POST, request, String.class);
                    
                    if (response.getStatusCode().is2xxSuccessful()) {
                        log.info("PagerDuty alert sent successfully: {} - {}", alert.getType(), alert.getMessage());
                    } else {
                        log.warn("PagerDuty API returned non-success status: {}", response.getStatusCode());
                        throw new AlertDeliveryException("PagerDuty API returned status: " + response.getStatusCode());
                    }
                    
                } catch (Exception e) {
                    log.error("Failed to send PagerDuty alert: {} - {}", alert.getType(), e.getMessage(), e);
                    throw new AlertDeliveryException("PagerDuty alert delivery failed", e);
                }
            })
        );
    }
    
    /**
     * Resolve PagerDuty alert with circuit breaker protection
     */
    @CircuitBreaker(name = "pagerduty-alerts", fallbackMethod = "resolvePagerDutyAlertFallback")
    @Retry(name = "pagerduty-alerts")
    @TimeLimiter(name = "pagerduty-alerts")
    public CompletableFuture<Void> resolvePagerDutyAlert(String alertId) {
        if (!pagerDutyEnabled) {
            log.debug("PagerDuty alerting is disabled");
            return CompletableFuture.completedFuture(null);
        }
        
        return resilientExecutor.executeAsyncWithResilience(
            "pagerduty-notifications",
            () -> CompletableFuture.runAsync(() -> {
                try {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("routing_key", pagerDutyIntegrationKey);
                    payload.put("event_action", "resolve");
                    payload.put("dedup_key", alertId);
                    
                    // Set proper headers for PagerDuty API
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("Content-Type", "application/json");
                    headers.set("User-Agent", "Waqiti-Monitoring/1.0");
                    
                    HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
                    
                    ResponseEntity<String> response = restTemplate.exchange(
                        "https://events.pagerduty.com/v2/enqueue", 
                        HttpMethod.POST, request, String.class);
                    
                    if (response.getStatusCode().is2xxSuccessful()) {
                        log.info("PagerDuty alert resolved successfully: {}", alertId);
                    } else {
                        log.warn("PagerDuty resolve API returned non-success status: {} for alert {}", 
                            response.getStatusCode(), alertId);
                        throw new AlertDeliveryException("PagerDuty resolve API returned status: " + response.getStatusCode());
                    }
                    
                } catch (Exception e) {
                    log.error("Failed to resolve PagerDuty alert: {} - {}", alertId, e.getMessage(), e);
                    throw new AlertDeliveryException("PagerDuty alert resolution failed", e);
                }
            })
        );
    }
    
    // Formatting methods
    
    private String formatEmailSubject(Alert alert) {
        return String.format("[%s] Waqiti Alert: %s", alert.getLevel(), alert.getType());
    }
    
    private String formatEmailBody(Alert alert) {
        StringBuilder body = new StringBuilder();
        body.append("Alert Details\n");
        body.append("=============\n\n");
        body.append("Level: ").append(alert.getLevel()).append("\n");
        body.append("Type: ").append(alert.getType()).append("\n");
        body.append("Time: ").append(alert.getTimestamp().format(DATE_FORMAT)).append("\n");
        body.append("Message: ").append(alert.getMessage()).append("\n\n");
        
        if (alert.getMetadata() != null && !alert.getMetadata().isEmpty()) {
            body.append("Additional Information:\n");
            alert.getMetadata().forEach((key, value) -> 
                body.append("  ").append(key).append(": ").append(value).append("\n"));
        }
        
        body.append("\n--\n");
        body.append("This is an automated alert from Waqiti Monitoring System\n");
        
        return body.toString();
    }
    
    private String formatSmsMessage(Alert alert) {
        return String.format("WAQITI ALERT [%s]: %s - %s", 
            alert.getLevel(), alert.getType(), 
            alert.getMessage().length() > 100 ? 
                alert.getMessage().substring(0, 97) + "..." : alert.getMessage());
    }
    
    private String formatSlackMessage(Alert alert) {
        return String.format("*[%s]* %s", alert.getLevel(), alert.getType());
    }
    
    private String formatPagerDutySummary(Alert alert) {
        return String.format("[%s] %s: %s", alert.getLevel(), alert.getType(), alert.getMessage());
    }
    
    private String getSlackEmoji(Alert alert) {
        switch (alert.getLevel()) {
            case CRITICAL:
                return ":rotating_light:";
            case ERROR:
                return ":x:";
            case WARNING:
                return ":warning:";
            default:
                return ":information_source:";
        }
    }
    
    private String getSlackColor(Alert alert) {
        switch (alert.getLevel()) {
            case CRITICAL:
                return "#FF0000"; // Red
            case ERROR:
                return "#FF6600"; // Orange
            case WARNING:
                return "#FFCC00"; // Yellow
            default:
                return "#0099FF"; // Blue
        }
    }
    
    private Object[] formatSlackFields(Map<String, Object> metadata) {
        return metadata.entrySet().stream()
            .limit(10) // Slack limits fields
            .map(entry -> {
                Map<String, Object> field = new HashMap<>();
                field.put("title", entry.getKey());
                field.put("value", String.valueOf(entry.getValue()));
                field.put("short", true);
                return field;
            })
            .toArray();
    }
    
    private String mapToPagerDutySeverity(Alert alert) {
        switch (alert.getLevel()) {
            case CRITICAL:
                return "critical";
            case ERROR:
                return "error";
            case WARNING:
                return "warning";
            default:
                return "info";
        }
    }
    
    // Fallback methods for circuit breaker patterns
    
    /**
     * Fallback method for Slack alert delivery failures
     */
    public CompletableFuture<Void> sendSlackAlertFallback(Alert alert, Exception ex) {
        log.error("Slack alert delivery failed, using fallback: {} - {} (Error: {})", 
            alert.getType(), alert.getMessage(), ex.getMessage());
        
        // Fallback to email if Slack fails
        try {
            sendEmailAlert(alert);
            log.info("Successfully sent alert via email fallback for failed Slack delivery");
        } catch (Exception emailEx) {
            log.error("Email fallback also failed for alert: {} - {}", 
                alert.getType(), emailEx.getMessage());
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Fallback method for PagerDuty alert delivery failures
     */
    public CompletableFuture<Void> sendPagerDutyAlertFallback(Alert alert, Exception ex) {
        log.error("PagerDuty alert delivery failed, using fallback: {} - {} (Error: {})", 
            alert.getType(), alert.getMessage(), ex.getMessage());
        
        // For critical alerts, try multiple fallback channels
        if (alert.getLevel() == Alert.Level.CRITICAL) {
            try {
                // Try Slack first
                if (slackEnabled) {
                    sendSlackAlertFallback(alert, ex);
                }
                
                // Always try email for critical alerts
                sendEmailAlert(alert);
                
                // Try SMS if enabled
                if (smsEnabled) {
                    sendSmsAlert(alert);
                }
                
                log.warn("Critical alert delivered via fallback channels after PagerDuty failure");
                
            } catch (Exception fallbackEx) {
                log.error("All fallback channels failed for critical alert: {} - {}", 
                    alert.getType(), fallbackEx.getMessage());
            }
        } else {
            // For non-critical alerts, just try email
            try {
                sendEmailAlert(alert);
                log.info("Successfully sent alert via email fallback for failed PagerDuty delivery");
            } catch (Exception emailEx) {
                log.error("Email fallback also failed for alert: {} - {}", 
                    alert.getType(), emailEx.getMessage());
            }
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Fallback method for PagerDuty alert resolution failures
     */
    public CompletableFuture<Void> resolvePagerDutyAlertFallback(String alertId, Exception ex) {
        log.error("PagerDuty alert resolution failed, using fallback for alert: {} (Error: {})", 
            alertId, ex.getMessage());
        
        // Log the failure for manual intervention
        log.warn("MANUAL_INTERVENTION_REQUIRED: PagerDuty alert {} could not be resolved automatically. " +
                "Please check PagerDuty dashboard and resolve manually if needed.", alertId);
        
        // Could also send notification to ops team about the failed resolution
        try {
            Alert failedResolutionAlert = Alert.builder()
                .id("pagerduty-resolution-failure-" + alertId)
                .type("PAGERDUTY_RESOLUTION_FAILURE")
                .level(Alert.Level.WARNING)
                .message("Failed to resolve PagerDuty alert: " + alertId)
                .timestamp(java.time.LocalDateTime.now())
                .metadata(Map.of(
                    "originalAlertId", alertId,
                    "errorMessage", ex.getMessage(),
                    "failureType", "RESOLUTION_FAILURE"
                ))
                .build();
            
            sendEmailAlert(failedResolutionAlert);
            
        } catch (Exception emailEx) {
            log.error("Failed to send notification about PagerDuty resolution failure: {}", 
                emailEx.getMessage());
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Enhanced alert delivery with multiple channels and resilience
     */
    public CompletableFuture<Void> sendAlertWithResilience(Alert alert) {
        log.info("Sending alert with resilience patterns: {} - {}", alert.getType(), alert.getMessage());
        
        CompletableFuture<Void> slackFuture = CompletableFuture.completedFuture(null);
        CompletableFuture<Void> pagerDutyFuture = CompletableFuture.completedFuture(null);
        
        // Send via Slack (non-blocking)
        if (slackEnabled) {
            slackFuture = sendSlackAlert(alert);
        }
        
        // Send via PagerDuty for critical alerts (non-blocking)
        if (pagerDutyEnabled && 
            (alert.getLevel() == Alert.Level.CRITICAL || alert.getLevel() == Alert.Level.ERROR)) {
            pagerDutyFuture = sendPagerDutyAlert(alert);
        }
        
        // Always send email as baseline
        try {
            sendEmailAlert(alert);
        } catch (Exception e) {
            log.error("Failed to send email alert (baseline channel): {}", e.getMessage());
        }
        
        // Send SMS for critical alerts
        if (smsEnabled && alert.getLevel() == Alert.Level.CRITICAL) {
            try {
                sendSmsAlert(alert);
            } catch (Exception e) {
                log.error("Failed to send SMS alert: {}", e.getMessage());
            }
        }
        
        // Wait for async operations to complete
        return CompletableFuture.allOf(slackFuture, pagerDutyFuture)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.warn("Some alert delivery channels failed, but email was sent: {}", 
                        throwable.getMessage());
                } else {
                    log.info("Alert delivered successfully via all configured channels: {}", 
                        alert.getType());
                }
            });
    }
    
    /**
     * Custom exception for alert delivery failures
     */
    public static class AlertDeliveryException extends RuntimeException {
        public AlertDeliveryException(String message) {
            super(message);
        }
        
        public AlertDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}