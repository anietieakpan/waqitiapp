package com.waqiti.frauddetection.alert;

import com.waqiti.frauddetection.dto.FraudAssessmentResult;
import com.waqiti.frauddetection.entity.FraudAlert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Fraud Alert Channel Manager
 * 
 * Manages delivery of fraud alerts through multiple channels:
 * - Email notifications
 * - SMS alerts
 * - Webhook callbacks
 * - Slack notifications
 * - PagerDuty incidents
 * 
 * @author Waqiti Security Team
 * @version 1.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FraudAlertChannelManager {

    private final JavaMailSender mailSender;
    private final RestTemplate restTemplate;
    
    @Value("${fraud.alert.email.recipients:fraud-team@example.com}")
    private String emailRecipients;
    
    @Value("${fraud.alert.email.from:fraud-alerts@example.com}")
    private String emailFrom;
    
    @Value("${fraud.alert.webhook.url:#{null}}")
    private String webhookUrl;
    
    @Value("${fraud.alert.slack.webhook.url:#{null}}")
    private String slackWebhookUrl;
    
    @Value("${fraud.alert.sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${fraud.alert.pagerduty.integration-key:#{null}}")
    private String pagerDutyIntegrationKey;

    @Value("${fraud.alert.pagerduty.api-url:https://events.pagerduty.com/v2/enqueue}")
    private String pagerDutyApiUrl;

    /**
     * Deliver alert through specified channel
     */
    public void deliverAlert(
            FraudAlertService.AlertChannel channel, 
            FraudAlert alert, 
            FraudAssessmentResult result) {
        
        switch (channel) {
            case EMAIL -> deliverEmailAlert(alert, result);
            case SMS -> deliverSMSAlert(alert, result);
            case WEBHOOK -> deliverWebhookAlert(alert, result);
            case SLACK -> deliverSlackAlert(alert, result);
            case PAGERDUTY -> deliverPagerDutyAlert(alert, result);
            case KAFKA -> {}
            default -> log.warn("Unsupported alert channel: {}", channel);
        }
    }

    /**
     * Deliver alert via email
     */
    private void deliverEmailAlert(FraudAlert alert, FraudAssessmentResult result) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(emailFrom);
            message.setTo(emailRecipients.split(","));
            message.setSubject(buildEmailSubject(alert));
            message.setText(buildEmailBody(alert, result));
            
            mailSender.send(message);
            
            log.info("Email alert sent for: {}", alert.getAlertId());
            
        } catch (Exception e) {
            log.error("Failed to send email alert: {}", e.getMessage());
        }
    }

    /**
     * Build email subject
     */
    private String buildEmailSubject(FraudAlert alert) {
        return String.format("[%s] Fraud Alert: %s - %s", 
            alert.getSeverity(), 
            alert.getAlertId(),
            alert.getTransactionId());
    }

    /**
     * Build email body
     */
    private String buildEmailBody(FraudAlert alert, FraudAssessmentResult result) {
        StringBuilder body = new StringBuilder();
        
        body.append("FRAUD ALERT NOTIFICATION\n");
        body.append("=".repeat(50)).append("\n\n");
        body.append("Alert ID: ").append(alert.getAlertId()).append("\n");
        body.append("Severity: ").append(alert.getSeverity()).append("\n");
        body.append("Transaction ID: ").append(alert.getTransactionId()).append("\n");
        body.append("User ID: ").append(alert.getUserId()).append("\n");
        body.append("Risk Score: ").append(String.format("%.2f", alert.getRiskScore())).append("\n");
        body.append("Status: ").append(alert.isBlocked() ? "BLOCKED" : "FLAGGED").append("\n\n");
        
        body.append("DETAILS:\n");
        body.append("-".repeat(50)).append("\n");
        body.append(alert.getDescription()).append("\n\n");
        
        body.append("ACTION REQUIRED:\n");
        body.append("-".repeat(50)).append("\n");
        if (alert.isBlocked()) {
            body.append("⚠️ This transaction has been BLOCKED.\n");
            body.append("Immediate manual review is required.\n");
        } else {
            body.append("Please review this transaction in the fraud management system.\n");
        }
        
        body.append("\nTimestamp: ").append(alert.getCreatedAt()).append("\n");
        
        return body.toString();
    }

    /**
     * Deliver alert via SMS
     */
    private void deliverSMSAlert(FraudAlert alert, FraudAssessmentResult result) {
        if (!smsEnabled) {
            log.debug("SMS alerts disabled, skipping");
            return;
        }
        
        try {
            String message = String.format(
                "FRAUD ALERT [%s]: Transaction %s blocked. Risk: %.2f. Alert: %s",
                alert.getSeverity(),
                alert.getTransactionId(),
                alert.getRiskScore(),
                alert.getAlertId()
            );
            
            log.info("SMS alert would be sent: {}", message);
            
        } catch (Exception e) {
            log.error("Failed to send SMS alert: {}", e.getMessage());
        }
    }

    /**
     * Deliver alert via webhook
     */
    private void deliverWebhookAlert(FraudAlert alert, FraudAssessmentResult result) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.debug("Webhook URL not configured, skipping");
            return;
        }
        
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("alertId", alert.getAlertId());
            payload.put("transactionId", alert.getTransactionId());
            payload.put("userId", alert.getUserId());
            payload.put("severity", alert.getSeverity());
            payload.put("riskScore", alert.getRiskScore());
            payload.put("isBlocked", alert.isBlocked());
            payload.put("timestamp", alert.getCreatedAt());
            payload.put("details", result);
            
            restTemplate.postForEntity(webhookUrl, payload, String.class);
            
            log.info("Webhook alert sent for: {}", alert.getAlertId());
            
        } catch (Exception e) {
            log.error("Failed to send webhook alert: {}", e.getMessage());
        }
    }

    /**
     * Deliver alert via Slack
     */
    private void deliverSlackAlert(FraudAlert alert, FraudAssessmentResult result) {
        if (slackWebhookUrl == null || slackWebhookUrl.isEmpty()) {
            log.debug("Slack webhook URL not configured, skipping");
            return;
        }
        
        try {
            Map<String, Object> slackPayload = buildSlackPayload(alert, result);
            
            restTemplate.postForEntity(slackWebhookUrl, slackPayload, String.class);
            
            log.info("Slack alert sent for: {}", alert.getAlertId());
            
        } catch (Exception e) {
            log.error("Failed to send Slack alert: {}", e.getMessage());
        }
    }

    /**
     * Build Slack message payload
     */
    private Map<String, Object> buildSlackPayload(FraudAlert alert, FraudAssessmentResult result) {
        Map<String, Object> payload = new HashMap<>();
        
        String color = switch (alert.getSeverity()) {
            case CRITICAL -> "danger";
            case HIGH -> "warning";
            case MEDIUM -> "#ff9900";
            case LOW -> "good";
        };
        
        String icon = alert.isBlocked() ? ":no_entry:" : ":warning:";
        
        Map<String, Object> attachment = new HashMap<>();
        attachment.put("color", color);
        attachment.put("title", icon + " " + alert.getTitle());
        attachment.put("text", alert.getDescription());
        attachment.put("footer", "Waqiti Fraud Detection");
        attachment.put("ts", System.currentTimeMillis() / 1000);
        
        payload.put("attachments", new Object[]{attachment});
        
        return payload;
    }

    /**
     * Deliver alert via PagerDuty
     */
    private void deliverPagerDutyAlert(FraudAlert alert, FraudAssessmentResult result) {
        if (pagerDutyIntegrationKey == null || pagerDutyIntegrationKey.isEmpty()) {
            log.debug("PagerDuty integration key not configured, skipping");
            return;
        }

        try {
            Map<String, Object> pagerDutyEvent = buildPagerDutyEvent(alert, result);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

            org.springframework.http.HttpEntity<Map<String, Object>> request =
                new org.springframework.http.HttpEntity<>(pagerDutyEvent, headers);

            org.springframework.http.ResponseEntity<String> response =
                restTemplate.postForEntity(pagerDutyApiUrl, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("PagerDuty alert sent successfully for: {}", alert.getAlertId());
            } else {
                log.error("PagerDuty alert failed with status: {}", response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Failed to send PagerDuty alert for {}: {}", alert.getAlertId(), e.getMessage());
        }
    }

    /**
     * Build PagerDuty event payload
     *
     * Documentation: https://developer.pagerduty.com/docs/events-api-v2/trigger-events/
     */
    private Map<String, Object> buildPagerDutyEvent(FraudAlert alert, FraudAssessmentResult result) {
        Map<String, Object> event = new HashMap<>();

        // Integration key (routing key)
        event.put("routing_key", pagerDutyIntegrationKey);

        // Event action: trigger, acknowledge, or resolve
        event.put("event_action", "trigger");

        // Dedup key for grouping related alerts
        event.put("dedup_key", "fraud-alert-" + alert.getTransactionId());

        // Payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("summary", buildPagerDutySummary(alert));
        payload.put("source", "Waqiti Fraud Detection Service");
        payload.put("severity", mapSeverityToPagerDuty(alert.getSeverity()));
        payload.put("timestamp", alert.getCreatedAt().toString());

        // Custom details
        Map<String, Object> customDetails = new HashMap<>();
        customDetails.put("alert_id", alert.getAlertId());
        customDetails.put("transaction_id", alert.getTransactionId());
        customDetails.put("user_id", alert.getUserId());
        customDetails.put("risk_score", alert.getRiskScore());
        customDetails.put("ml_score", alert.getMlScore());
        customDetails.put("rule_score", alert.getRuleScore());
        customDetails.put("is_blocked", alert.isBlocked());
        customDetails.put("triggered_rules", alert.getTriggeredRules());
        customDetails.put("requires_manual_review", alert.isRequiresManualReview());

        payload.put("custom_details", customDetails);

        event.put("payload", payload);

        // Links
        Map<String, Object>[] links = new Map[]{
            Map.of(
                "href", "https://fraud.example.com/alerts/" + alert.getAlertId(),
                "text", "View Alert Details"
            ),
            Map.of(
                "href", "https://fraud.example.com/transactions/" + alert.getTransactionId(),
                "text", "View Transaction"
            )
        };
        event.put("links", links);

        return event;
    }

    /**
     * Build PagerDuty summary text
     */
    private String buildPagerDutySummary(FraudAlert alert) {
        return String.format("[%s] Fraud Alert %s: Transaction %s - Risk Score: %.2f",
            alert.getSeverity(),
            alert.getAlertId(),
            alert.getTransactionId(),
            alert.getRiskScore()
        );
    }

    /**
     * Map alert severity to PagerDuty severity levels
     * PagerDuty severities: critical, error, warning, info
     */
    private String mapSeverityToPagerDuty(FraudAlertService.AlertSeverity severity) {
        return switch (severity) {
            case CRITICAL -> "critical";
            case HIGH -> "error";
            case MEDIUM -> "warning";
            case LOW -> "info";
        };
    }
}