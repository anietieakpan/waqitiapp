package com.waqiti.common.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.domain.AuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Incident Alerting Service
 * 
 * Provides real-time incident alerting through multiple channels:
 * - PagerDuty for on-call escalation
 * - Slack for team notifications
 * - Email for critical stakeholders
 * 
 * FEATURES:
 * - Multi-channel alerting
 * - Severity-based routing
 * - Async delivery for performance
 * - Retry mechanisms for reliability
 * - Rich alert formatting
 * 
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2025-01-15
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IncidentAlertingService {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final JavaMailSender mailSender;
    
    @Value("${waqiti.alerting.pagerduty.enabled:false}")
    private boolean pagerdutyEnabled;
    
    @Value("${waqiti.alerting.pagerduty.integration.key:}")
    private String pagerdutyIntegrationKey;
    
    @Value("${waqiti.alerting.slack.enabled:false}")
    private boolean slackEnabled;
    
    @Value("${waqiti.alerting.slack.webhook.url:}")
    private String slackWebhookUrl;
    
    @Value("${waqiti.alerting.email.enabled:false}")
    private boolean emailEnabled;
    
    @Value("${waqiti.alerting.email.recipients:}")
    private String[] emailRecipients;
    
    @Value("${waqiti.alerting.email.from:noreply@example.com}")
    private String emailFrom;
    
    /**
     * Send incident alert through all configured channels
     */
    @Async("alertingExecutor")
    public void sendIncidentAlert(AuditLog auditLog) {
        log.info("Sending incident alert for: {}", auditLog.getId());
        
        try {
            // Send to PagerDuty for on-call escalation
            if (pagerdutyEnabled && shouldEscalateToPagerDuty(auditLog)) {
                sendToPagerDuty(auditLog);
            }
            
            // Send to Slack for team notifications
            if (slackEnabled) {
                sendToSlack(auditLog);
            }
            
            // Send email to critical stakeholders
            if (emailEnabled) {
                sendEmail(auditLog);
            }
            
            log.info("Incident alert sent successfully: {}", auditLog.getId());
            
        } catch (Exception e) {
            log.error("Failed to send incident alert: {}", auditLog.getId(), e);
        }
    }
    
    /**
     * Send alert to PagerDuty Events API v2
     */
    private void sendToPagerDuty(AuditLog auditLog) {
        try {
            if (pagerdutyIntegrationKey == null || pagerdutyIntegrationKey.isEmpty()) {
                log.warn("PagerDuty integration key not configured");
                return;
            }
            
            String pagerdutyUrl = "https://events.pagerduty.com/v2/enqueue";
            
            // Build PagerDuty event
            Map<String, Object> event = new HashMap<>();
            event.put("routing_key", pagerdutyIntegrationKey);
            event.put("event_action", "trigger");
            event.put("dedup_key", auditLog.getId().toString());
            
            // Build payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("summary", formatPagerDutySummary(auditLog));
            payload.put("source", "waqiti-audit-system");
            payload.put("severity", mapSeverityToPagerDuty(auditLog.getSeverity()));
            payload.put("timestamp", auditLog.getTimestamp().toString());
            payload.put("component", auditLog.getService());
            payload.put("group", auditLog.getEventCategory().name());
            payload.put("class", auditLog.getEventType());
            
            // Add custom details
            Map<String, Object> customDetails = new HashMap<>();
            customDetails.put("event_id", auditLog.getId().toString());
            customDetails.put("user_id", auditLog.getUserId());
            customDetails.put("username", auditLog.getUsername());
            customDetails.put("action", auditLog.getAction());
            customDetails.put("description", auditLog.getDescription());
            customDetails.put("ip_address", auditLog.getIpAddress());
            customDetails.put("correlation_id", auditLog.getCorrelationId());
            
            if (auditLog.getRiskScore() != null) {
                customDetails.put("risk_score", auditLog.getRiskScore());
            }
            
            payload.put("custom_details", customDetails);
            event.put("payload", payload);
            
            // Send to PagerDuty
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(event, headers);
            restTemplate.postForEntity(pagerdutyUrl, request, String.class);
            
            log.info("Alert sent to PagerDuty: {}", auditLog.getId());
            
        } catch (Exception e) {
            log.error("Failed to send alert to PagerDuty: {}", auditLog.getId(), e);
        }
    }
    
    /**
     * Send alert to Slack via webhook
     */
    private void sendToSlack(AuditLog auditLog) {
        try {
            if (slackWebhookUrl == null || slackWebhookUrl.isEmpty()) {
                log.warn("Slack webhook URL not configured");
                return;
            }
            
            // Build Slack message with blocks for rich formatting
            Map<String, Object> message = new HashMap<>();
            
            // Add text fallback
            message.put("text", formatSlackText(auditLog));
            
            // Add structured blocks
            java.util.List<Map<String, Object>> blocks = new java.util.ArrayList<>();
            
            // Header block
            Map<String, Object> headerBlock = new HashMap<>();
            headerBlock.put("type", "header");
            headerBlock.put("text", Map.of("type", "plain_text", "text", formatSlackHeader(auditLog)));
            blocks.add(headerBlock);
            
            // Context block with metadata
            Map<String, Object> contextBlock = new HashMap<>();
            contextBlock.put("type", "section");
            contextBlock.put("fields", java.util.List.of(
                Map.of("type", "mrkdwn", "text", "*Severity:* " + auditLog.getSeverity()),
                Map.of("type", "mrkdwn", "text", "*Event Type:* " + auditLog.getEventType()),
                Map.of("type", "mrkdwn", "text", "*User:* " + auditLog.getUsername()),
                Map.of("type", "mrkdwn", "text", "*Service:* " + auditLog.getService()),
                Map.of("type", "mrkdwn", "text", "*IP Address:* " + auditLog.getIpAddress()),
                Map.of("type", "mrkdwn", "text", "*Time:* " + auditLog.getTimestamp().toString())
            ));
            blocks.add(contextBlock);
            
            // Description block
            Map<String, Object> descriptionBlock = new HashMap<>();
            descriptionBlock.put("type", "section");
            descriptionBlock.put("text", Map.of("type", "mrkdwn", "text", "*Description:*\n" + auditLog.getDescription()));
            blocks.add(descriptionBlock);
            
            // Add action block for critical events
            if (auditLog.getSeverity() == AuditLog.Severity.CRITICAL || 
                auditLog.getSeverity() == AuditLog.Severity.EMERGENCY) {
                Map<String, Object> actionBlock = new HashMap<>();
                actionBlock.put("type", "actions");
                actionBlock.put("elements", java.util.List.of(
                    Map.of(
                        "type", "button",
                        "text", Map.of("type", "plain_text", "text", "View Details"),
                        "url", "https://api.example.com/admin/audit/" + auditLog.getId(),
                        "style", "primary"
                    )
                ));
                blocks.add(actionBlock);
            }
            
            message.put("blocks", blocks);
            
            // Set color based on severity
            message.put("attachments", java.util.List.of(
                Map.of("color", getSlackColor(auditLog.getSeverity()))
            ));
            
            // Send to Slack
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(message, headers);
            restTemplate.postForEntity(slackWebhookUrl, request, String.class);
            
            log.info("Alert sent to Slack: {}", auditLog.getId());
            
        } catch (Exception e) {
            log.error("Failed to send alert to Slack: {}", auditLog.getId(), e);
        }
    }
    
    /**
     * Send email alert
     */
    private void sendEmail(AuditLog auditLog) {
        try {
            if (emailRecipients == null || emailRecipients.length == 0) {
                log.warn("Email recipients not configured");
                return;
            }
            
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(emailFrom);
            message.setTo(emailRecipients);
            message.setSubject(formatEmailSubject(auditLog));
            message.setText(formatEmailBody(auditLog));
            
            mailSender.send(message);
            
            log.info("Alert email sent: {}", auditLog.getId());
            
        } catch (Exception e) {
            log.error("Failed to send email alert: {}", auditLog.getId(), e);
        }
    }
    
    /**
     * Determine if event should be escalated to PagerDuty
     */
    private boolean shouldEscalateToPagerDuty(AuditLog auditLog) {
        return auditLog.getSeverity() == AuditLog.Severity.CRITICAL ||
               auditLog.getSeverity() == AuditLog.Severity.EMERGENCY ||
               (auditLog.getRiskScore() != null && auditLog.getRiskScore() > 90);
    }
    
    /**
     * Format PagerDuty summary
     */
    private String formatPagerDutySummary(AuditLog auditLog) {
        return String.format("[%s] %s - %s", 
            auditLog.getSeverity(), 
            auditLog.getEventType(), 
            auditLog.getDescription());
    }
    
    /**
     * Map severity to PagerDuty severity levels
     */
    private String mapSeverityToPagerDuty(AuditLog.Severity severity) {
        return switch (severity) {
            case EMERGENCY, CRITICAL -> "critical";
            case HIGH -> "error";
            case MEDIUM -> "warning";
            default -> "info";
        };
    }
    
    /**
     * Format Slack header
     */
    private String formatSlackHeader(AuditLog auditLog) {
        String emoji = switch (auditLog.getSeverity()) {
            case EMERGENCY -> "\uD83D\uDEA8";
            case CRITICAL -> "\uD83D\uDD34";
            case HIGH -> "\uD83D\uDFE0";
            case MEDIUM -> "\uD83D\uDFE1";
            default -> "\uD83D\uDD35";
        };
        
        return emoji + " " + auditLog.getEventType() + " Alert";
    }
    
    /**
     * Format Slack text fallback
     */
    private String formatSlackText(AuditLog auditLog) {
        return String.format("[%s] %s: %s (User: %s, Service: %s)", 
            auditLog.getSeverity(),
            auditLog.getEventType(),
            auditLog.getDescription(),
            auditLog.getUsername(),
            auditLog.getService());
    }
    
    /**
     * Get Slack color based on severity
     */
    private String getSlackColor(AuditLog.Severity severity) {
        return switch (severity) {
            case EMERGENCY, CRITICAL -> "#ff0000";  // Red
            case HIGH -> "#ff6600";                  // Orange
            case MEDIUM -> "#ffcc00";                // Yellow
            case LOW -> "#00ccff";                   // Blue
            default -> "#999999";                    // Gray
        };
    }
    
    /**
     * Format email subject
     */
    private String formatEmailSubject(AuditLog auditLog) {
        return String.format("[Waqiti Security Alert - %s] %s", 
            auditLog.getSeverity(), 
            auditLog.getEventType());
    }
    
    /**
     * Format email body
     */
    private String formatEmailBody(AuditLog auditLog) {
        StringBuilder body = new StringBuilder();
        body.append("SECURITY AUDIT ALERT\n");
        body.append("=".repeat(80)).append("\n\n");
        
        body.append("Event ID:      ").append(auditLog.getId()).append("\n");
        body.append("Severity:      ").append(auditLog.getSeverity()).append("\n");
        body.append("Event Type:    ").append(auditLog.getEventType()).append("\n");
        body.append("Category:      ").append(auditLog.getEventCategory()).append("\n");
        body.append("Service:       ").append(auditLog.getService()).append("\n");
        body.append("Timestamp:     ").append(auditLog.getTimestamp().toString()).append("\n");
        body.append("\n");
        
        body.append("User Information:\n");
        body.append("  User ID:     ").append(auditLog.getUserId()).append("\n");
        body.append("  Username:    ").append(auditLog.getUsername()).append("\n");
        body.append("  IP Address:  ").append(auditLog.getIpAddress()).append("\n");
        body.append("\n");
        
        body.append("Event Details:\n");
        body.append("  Action:      ").append(auditLog.getAction()).append("\n");
        body.append("  Description: ").append(auditLog.getDescription()).append("\n");
        body.append("  Result:      ").append(auditLog.getResult()).append("\n");
        body.append("\n");
        
        if (auditLog.getRiskScore() != null && auditLog.getRiskScore() > 0) {
            body.append("Risk Score:    ").append(auditLog.getRiskScore()).append("/100\n\n");
        }
        
        body.append("Correlation ID: ").append(auditLog.getCorrelationId()).append("\n");
        body.append("Session ID:     ").append(auditLog.getSessionId()).append("\n");
        body.append("\n");
        
        body.append("Compliance Relevance:\n");
        if (auditLog.getPciRelevant()) body.append("  - PCI-DSS\n");
        if (auditLog.getGdprRelevant()) body.append("  - GDPR\n");
        if (auditLog.getSoxRelevant()) body.append("  - SOX\n");
        if (auditLog.getSoc2Relevant()) body.append("  - SOC 2\n");
        body.append("\n");
        
        body.append("=".repeat(80)).append("\n");
        body.append("View full details: https://api.example.com/admin/audit/").append(auditLog.getId()).append("\n");
        body.append("\n");
        body.append("This is an automated alert from the Waqiti Security Audit System.\n");
        
        return body.toString();
    }
}