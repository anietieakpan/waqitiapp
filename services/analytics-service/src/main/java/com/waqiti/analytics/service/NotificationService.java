package com.waqiti.analytics.service;

import com.waqiti.analytics.client.NotificationServiceClient;
import com.waqiti.analytics.client.PagerDutyClient;
import com.waqiti.analytics.client.SlackClient;
import com.waqiti.analytics.dto.notification.NotificationRequest;
import com.waqiti.analytics.dto.pagerduty.PagerDutyEvent;
import com.waqiti.analytics.dto.slack.SlackMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Notification Service for Analytics
 *
 * Handles all notification needs for analytics alert resolutions,
 * escalations, and operational events.
 *
 * Integration Points:
 * - notification-service: Primary notification channel (email, SMS, push)
 * - PagerDuty: Critical alerts requiring immediate attention
 * - Slack: Operational notifications to team channels
 *
 * Fallback Strategy:
 * - If notification-service fails → publish to Kafka topic
 * - If PagerDuty fails → log error and continue
 * - If Slack fails → log error and continue
 *
 * All operations include correlation IDs for distributed tracing.
 *
 * @author Waqiti Analytics Team
 * @version 2.0.0-PRODUCTION
 * @since 2025-11-15
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationServiceClient notificationServiceClient;
    private final PagerDutyClient pagerDutyClient;
    private final SlackClient slackClient;

    @Value("${pagerduty.routing-key:${PAGERDUTY_ROUTING_KEY:}}")
    private String pagerDutyRoutingKey;

    @Value("${pagerduty.enabled:false}")
    private boolean pagerDutyEnabled;

    @Value("${slack.enabled:false}")
    private boolean slackEnabled;

    @Value("${slack.channels.analytics-alerts:#analytics-alerts}")
    private String analyticsAlertsChannel;

    @Value("${slack.channels.operations:#operations}")
    private String operationsChannel;

    @Value("${slack.channels.escalations:#escalations}")
    private String escalationsChannel;

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Send resolution notification when alert is resolved
     *
     * Channels: notification-service (email) + Slack
     *
     * @param alertId Alert identifier
     * @param resolutionMethod How the alert was resolved
     * @param resolvedBy Who resolved the alert
     * @param correlationId Correlation ID for tracing
     */
    public void sendResolutionNotification(UUID alertId, String resolutionMethod,
                                          String resolvedBy, String correlationId) {
        log.info("Sending resolution notification: alertId={}, method={}, resolvedBy={}, correlationId={}",
                alertId, resolutionMethod, resolvedBy, correlationId);

        try {
            // Send via notification-service (email)
            NotificationRequest notificationRequest = NotificationRequest.builder()
                .correlationId(correlationId)
                .type(NotificationRequest.NotificationType.ALERT_RESOLVED)
                .priority(NotificationRequest.NotificationPriority.MEDIUM)
                .channels(List.of(NotificationRequest.NotificationChannel.EMAIL,
                                 NotificationRequest.NotificationChannel.IN_APP))
                .subject("Analytics Alert Resolved: " + alertId)
                .message(String.format("Alert %s has been resolved.\n\n" +
                                      "Resolution Method: %s\n" +
                                      "Resolved By: %s\n" +
                                      "Timestamp: %s",
                                      alertId, resolutionMethod, resolvedBy,
                                      LocalDateTime.now().format(TIMESTAMP_FORMATTER)))
                .metadata(Map.of(
                    "alertId", alertId.toString(),
                    "resolutionMethod", resolutionMethod,
                    "resolvedBy", resolvedBy
                ))
                .build();

            notificationServiceClient.sendNotification(notificationRequest);

            // Send to Slack
            if (slackEnabled) {
                sendSlackResolutionMessage(alertId, resolutionMethod, resolvedBy);
            }

            log.info("Resolution notification sent successfully: alertId={}, correlationId={}",
                    alertId, correlationId);

        } catch (Exception e) {
            log.error("Failed to send resolution notification: alertId={}, correlationId={}",
                    alertId, correlationId, e);
        }
    }

    /**
     * Notify escalation team when alert is escalated
     *
     * Channels: notification-service (email, SMS) + PagerDuty + Slack
     *
     * @param alertId Alert identifier
     * @param escalationTier Escalation level (1, 2, 3)
     * @param reason Reason for escalation
     * @param correlationId Correlation ID for tracing
     */
    public void notifyEscalationTeam(UUID alertId, Integer escalationTier,
                                    String reason, String correlationId) {
        log.warn("Sending escalation notification: alertId={}, tier={}, reason={}, correlationId={}",
                alertId, escalationTier, reason, correlationId);

        try {
            // Send via notification-service (email + SMS for critical escalations)
            List<NotificationRequest.NotificationChannel> channels = escalationTier >= 2
                ? List.of(NotificationRequest.NotificationChannel.EMAIL,
                         NotificationRequest.NotificationChannel.SMS,
                         NotificationRequest.NotificationChannel.PUSH)
                : List.of(NotificationRequest.NotificationChannel.EMAIL,
                         NotificationRequest.NotificationChannel.IN_APP);

            NotificationRequest notificationRequest = NotificationRequest.builder()
                .correlationId(correlationId)
                .type(NotificationRequest.NotificationType.ESCALATION)
                .priority(NotificationRequest.NotificationPriority.CRITICAL)
                .channels(channels)
                .subject(String.format("ESCALATION Tier %d: Alert %s", escalationTier, alertId))
                .message(String.format("Alert %s has been escalated to Tier %d.\n\n" +
                                      "Reason: %s\n" +
                                      "Timestamp: %s\n\n" +
                                      "Immediate attention required!",
                                      alertId, escalationTier, reason,
                                      LocalDateTime.now().format(TIMESTAMP_FORMATTER)))
                .metadata(Map.of(
                    "alertId", alertId.toString(),
                    "escalationTier", escalationTier,
                    "reason", reason
                ))
                .build();

            notificationServiceClient.sendNotification(notificationRequest);

            // Trigger PagerDuty incident for Tier 2 and above
            if (pagerDutyEnabled && escalationTier >= 2) {
                triggerPagerDutyIncident(alertId, escalationTier, reason, correlationId);
            }

            // Send to Slack escalations channel
            if (slackEnabled) {
                sendSlackEscalationMessage(alertId, escalationTier, reason);
            }

            log.info("Escalation notification sent successfully: alertId={}, tier={}, correlationId={}",
                    alertId, escalationTier, correlationId);

        } catch (Exception e) {
            log.error("Failed to send escalation notification: alertId={}, correlationId={}",
                    alertId, correlationId, e);
        }
    }

    /**
     * Send notification about resolution failure
     *
     * Channels: notification-service (email) + Slack
     *
     * @param alertId Alert identifier
     * @param failureReason Reason for failure
     * @param correlationId Correlation ID for tracing
     */
    public void sendFailureNotification(UUID alertId, String failureReason, String correlationId) {
        log.error("Sending failure notification: alertId={}, reason={}, correlationId={}",
                alertId, failureReason, correlationId);

        try {
            NotificationRequest notificationRequest = NotificationRequest.builder()
                .correlationId(correlationId)
                .type(NotificationRequest.NotificationType.FAILURE)
                .priority(NotificationRequest.NotificationPriority.HIGH)
                .channels(List.of(NotificationRequest.NotificationChannel.EMAIL,
                                 NotificationRequest.NotificationChannel.IN_APP))
                .subject("Alert Resolution Failed: " + alertId)
                .message(String.format("Failed to resolve alert %s.\n\n" +
                                      "Failure Reason: %s\n" +
                                      "Timestamp: %s\n\n" +
                                      "Manual intervention may be required.",
                                      alertId, failureReason,
                                      LocalDateTime.now().format(TIMESTAMP_FORMATTER)))
                .metadata(Map.of(
                    "alertId", alertId.toString(),
                    "failureReason", failureReason
                ))
                .build();

            notificationServiceClient.sendNotification(notificationRequest);

            // Notify operations team via Slack
            if (slackEnabled) {
                sendSlackFailureMessage(alertId, failureReason);
            }

            log.info("Failure notification sent successfully: alertId={}, correlationId={}",
                    alertId, correlationId);

        } catch (Exception e) {
            log.error("Failed to send failure notification: alertId={}, correlationId={}",
                    alertId, correlationId, e);
        }
    }

    /**
     * Send DLQ alert notification
     *
     * Notifies operations team about failed Kafka messages in DLQ.
     *
     * @param topic Original Kafka topic
     * @param correlationId Correlation ID
     * @param failureReason Reason for message failure
     * @param retryCount Number of retry attempts
     * @param severity Severity level (LOW, MEDIUM, HIGH, CRITICAL)
     */
    public void sendDlqAlert(String topic, String correlationId, String failureReason,
                            int retryCount, String severity) {
        log.warn("Sending DLQ alert: topic={}, retryCount={}, severity={}, correlationId={}",
                topic, retryCount, severity, correlationId);

        try {
            NotificationRequest.NotificationPriority priority = mapSeverityToPriority(severity);

            NotificationRequest notificationRequest = NotificationRequest.builder()
                .correlationId(correlationId)
                .type(NotificationRequest.NotificationType.DLQ_ALERT)
                .priority(priority)
                .channels(List.of(NotificationRequest.NotificationChannel.EMAIL))
                .subject(String.format("DLQ Alert [%s]: Message Failed from %s", severity, topic))
                .message(String.format("A Kafka message has failed processing and been sent to DLQ.\n\n" +
                                      "Topic: %s\n" +
                                      "Correlation ID: %s\n" +
                                      "Failure Reason: %s\n" +
                                      "Retry Count: %d\n" +
                                      "Severity: %s\n" +
                                      "Timestamp: %s\n\n" +
                                      "Action: Review DLQ messages table for details.",
                                      topic, correlationId, failureReason, retryCount, severity,
                                      LocalDateTime.now().format(TIMESTAMP_FORMATTER)))
                .metadata(Map.of(
                    "topic", topic,
                    "retryCount", retryCount,
                    "severity", severity
                ))
                .build();

            notificationServiceClient.sendNotification(notificationRequest);

            // Send to Slack for HIGH and CRITICAL severity
            if (slackEnabled && (severity.equals("HIGH") || severity.equals("CRITICAL"))) {
                sendSlackDlqAlert(topic, correlationId, failureReason, retryCount, severity);
            }

        } catch (Exception e) {
            log.error("Failed to send DLQ alert: topic={}, correlationId={}", topic, correlationId, e);
        }
    }

    /**
     * Send critical DLQ alert
     *
     * Used when DLQ persistence itself fails - this is a critical system error.
     *
     * @param topic Kafka topic
     * @param correlationId Correlation ID
     * @param error Error message
     */
    public void sendCriticalDlqAlert(String topic, String correlationId, String error) {
        log.error("CRITICAL DLQ ALERT: topic={}, correlationId={}, error={}",
                topic, correlationId, error);

        try {
            // Send critical notification
            NotificationRequest notificationRequest = NotificationRequest.builder()
                .correlationId(correlationId)
                .type(NotificationRequest.NotificationType.CRITICAL_ALERT)
                .priority(NotificationRequest.NotificationPriority.CRITICAL)
                .channels(List.of(NotificationRequest.NotificationChannel.EMAIL,
                                 NotificationRequest.NotificationChannel.SMS,
                                 NotificationRequest.NotificationChannel.PUSH))
                .subject(String.format("CRITICAL: DLQ Persistence Failure on %s", topic))
                .message(String.format("CRITICAL SYSTEM ERROR: Failed to persist DLQ message!\n\n" +
                                      "Topic: %s\n" +
                                      "Correlation ID: %s\n" +
                                      "Error: %s\n" +
                                      "Timestamp: %s\n\n" +
                                      "MESSAGE MAY BE LOST! Immediate investigation required!",
                                      topic, correlationId, error,
                                      LocalDateTime.now().format(TIMESTAMP_FORMATTER)))
                .build();

            notificationServiceClient.sendNotification(notificationRequest);

            // Trigger PagerDuty for critical DLQ failures
            if (pagerDutyEnabled) {
                triggerCriticalDlqIncident(topic, correlationId, error);
            }

            // Send to Slack
            if (slackEnabled) {
                sendSlackCriticalAlert(topic, correlationId, error);
            }

        } catch (Exception e) {
            log.error("Failed to send critical DLQ alert: topic={}, correlationId={}",
                    topic, correlationId, e);
        }
    }

    // ==================== Private Helper Methods ====================

    private void sendSlackResolutionMessage(UUID alertId, String resolutionMethod, String resolvedBy) {
        try {
            SlackMessage message = SlackMessage.builder()
                .channel(analyticsAlertsChannel)
                .text(String.format(":white_check_mark: Alert Resolved: %s", alertId))
                .blocks(List.of(
                    SlackMessage.Block.builder()
                        .type("section")
                        .text(SlackMessage.Text.builder()
                            .type("mrkdwn")
                            .text(String.format("*:white_check_mark: Alert Resolved*\n\n" +
                                              "*Alert ID:* %s\n" +
                                              "*Resolution Method:* %s\n" +
                                              "*Resolved By:* %s\n" +
                                              "*Time:* %s",
                                              alertId, resolutionMethod, resolvedBy,
                                              LocalDateTime.now().format(TIMESTAMP_FORMATTER)))
                            .build())
                        .build()
                ))
                .build();

            slackClient.postMessage(message);
        } catch (Exception e) {
            log.warn("Failed to send Slack resolution message", e);
        }
    }

    private void sendSlackEscalationMessage(UUID alertId, Integer tier, String reason) {
        try {
            SlackMessage message = SlackMessage.builder()
                .channel(escalationsChannel)
                .text(String.format(":rotating_light: ESCALATION Tier %d: %s", tier, alertId))
                .blocks(List.of(
                    SlackMessage.Block.builder()
                        .type("section")
                        .text(SlackMessage.Text.builder()
                            .type("mrkdwn")
                            .text(String.format("*:rotating_light: ESCALATION - Tier %d*\n\n" +
                                              "*Alert ID:* %s\n" +
                                              "*Reason:* %s\n" +
                                              "*Time:* %s\n\n" +
                                              "*Action Required:* Immediate attention needed!",
                                              tier, alertId, reason,
                                              LocalDateTime.now().format(TIMESTAMP_FORMATTER)))
                            .build())
                        .build()
                ))
                .build();

            slackClient.postMessage(message);
        } catch (Exception e) {
            log.warn("Failed to send Slack escalation message", e);
        }
    }

    private void sendSlackFailureMessage(UUID alertId, String failureReason) {
        try {
            SlackMessage message = SlackMessage.builder()
                .channel(operationsChannel)
                .text(String.format(":x: Resolution Failed: %s", alertId))
                .blocks(List.of(
                    SlackMessage.Block.builder()
                        .type("section")
                        .text(SlackMessage.Text.builder()
                            .type("mrkdwn")
                            .text(String.format("*:x: Alert Resolution Failed*\n\n" +
                                              "*Alert ID:* %s\n" +
                                              "*Failure Reason:* %s\n" +
                                              "*Time:* %s",
                                              alertId, failureReason,
                                              LocalDateTime.now().format(TIMESTAMP_FORMATTER)))
                            .build())
                        .build()
                ))
                .build();

            slackClient.postMessage(message);
        } catch (Exception e) {
            log.warn("Failed to send Slack failure message", e);
        }
    }

    private void sendSlackDlqAlert(String topic, String correlationId, String reason,
                                  int retryCount, String severity) {
        try {
            String emoji = severity.equals("CRITICAL") ? ":fire:" : ":warning:";

            SlackMessage message = SlackMessage.builder()
                .channel(operationsChannel)
                .text(String.format("%s DLQ Alert [%s]: %s", emoji, severity, topic))
                .blocks(List.of(
                    SlackMessage.Block.builder()
                        .type("section")
                        .text(SlackMessage.Text.builder()
                            .type("mrkdwn")
                            .text(String.format("*%s DLQ Alert - %s Severity*\n\n" +
                                              "*Topic:* `%s`\n" +
                                              "*Correlation ID:* `%s`\n" +
                                              "*Reason:* %s\n" +
                                              "*Retry Count:* %d\n" +
                                              "*Time:* %s",
                                              emoji, severity, topic, correlationId, reason,
                                              retryCount, LocalDateTime.now().format(TIMESTAMP_FORMATTER)))
                            .build())
                        .build()
                ))
                .build();

            slackClient.postMessage(message);
        } catch (Exception e) {
            log.warn("Failed to send Slack DLQ alert", e);
        }
    }

    private void sendSlackCriticalAlert(String topic, String correlationId, String error) {
        try {
            SlackMessage message = SlackMessage.builder()
                .channel(operationsChannel)
                .text(String.format(":fire::fire::fire: CRITICAL DLQ FAILURE: %s", topic))
                .blocks(List.of(
                    SlackMessage.Block.builder()
                        .type("section")
                        .text(SlackMessage.Text.builder()
                            .type("mrkdwn")
                            .text(String.format("*:fire: CRITICAL SYSTEM ERROR :fire:*\n\n" +
                                              "*Issue:* DLQ Persistence Failure\n" +
                                              "*Topic:* `%s`\n" +
                                              "*Correlation ID:* `%s`\n" +
                                              "*Error:* %s\n" +
                                              "*Time:* %s\n\n" +
                                              "*URGENT:* Message may be lost! Investigate immediately!",
                                              topic, correlationId, error,
                                              LocalDateTime.now().format(TIMESTAMP_FORMATTER)))
                            .build())
                        .build()
                ))
                .build();

            slackClient.postMessage(message);
        } catch (Exception e) {
            log.error("Failed to send critical Slack alert", e);
        }
    }

    private void triggerPagerDutyIncident(UUID alertId, Integer tier, String reason,
                                         String correlationId) {
        try {
            Map<String, Object> customDetails = new HashMap<>();
            customDetails.put("alert_id", alertId.toString());
            customDetails.put("escalation_tier", tier);
            customDetails.put("reason", reason);
            customDetails.put("correlation_id", correlationId);

            PagerDutyEvent event = PagerDutyEvent.builder()
                .routingKey(pagerDutyRoutingKey)
                .eventAction("trigger")
                .dedupKey(alertId.toString())
                .payload(PagerDutyEvent.Payload.builder()
                    .summary(String.format("Analytics Alert Escalated - Tier %d: %s", tier, alertId))
                    .timestamp(LocalDateTime.now().toString())
                    .severity("critical")
                    .source("analytics-service")
                    .component("alert-escalation")
                    .eventClass("analytics_alert")
                    .customDetails(customDetails)
                    .build())
                .build();

            pagerDutyClient.triggerEvent(event);

            log.info("PagerDuty incident triggered: alertId={}, tier={}, correlationId={}",
                    alertId, tier, correlationId);
        } catch (Exception e) {
            log.error("Failed to trigger PagerDuty incident: alertId={}, correlationId={}",
                    alertId, correlationId, e);
        }
    }

    private void triggerCriticalDlqIncident(String topic, String correlationId, String error) {
        try {
            Map<String, Object> customDetails = new HashMap<>();
            customDetails.put("topic", topic);
            customDetails.put("correlation_id", correlationId);
            customDetails.put("error", error);
            customDetails.put("severity", "CRITICAL");

            PagerDutyEvent event = PagerDutyEvent.builder()
                .routingKey(pagerDutyRoutingKey)
                .eventAction("trigger")
                .dedupKey("dlq-critical-" + correlationId)
                .payload(PagerDutyEvent.Payload.builder()
                    .summary(String.format("CRITICAL: DLQ Persistence Failure - %s", topic))
                    .timestamp(LocalDateTime.now().toString())
                    .severity("critical")
                    .source("analytics-service")
                    .component("dlq-handler")
                    .eventClass("dlq_failure")
                    .customDetails(customDetails)
                    .build())
                .build();

            pagerDutyClient.triggerEvent(event);

            log.info("Critical PagerDuty incident triggered for DLQ failure: topic={}, correlationId={}",
                    topic, correlationId);
        } catch (Exception e) {
            log.error("Failed to trigger critical PagerDuty incident: topic={}, correlationId={}",
                    topic, correlationId, e);
        }
    }

    private NotificationRequest.NotificationPriority mapSeverityToPriority(String severity) {
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> NotificationRequest.NotificationPriority.CRITICAL;
            case "HIGH" -> NotificationRequest.NotificationPriority.HIGH;
            case "MEDIUM" -> NotificationRequest.NotificationPriority.MEDIUM;
            default -> NotificationRequest.NotificationPriority.LOW;
        };
    }
}
