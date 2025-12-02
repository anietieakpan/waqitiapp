package com.waqiti.common.alert;

import com.waqiti.common.alert.dto.SlackAttachment;
import com.waqiti.common.alert.dto.SlackMessage;
import com.waqiti.common.alert.enums.AlertSeverity;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Centralized Alert Service - Production-Ready Multi-Channel Alerting
 *
 * Provides unified interface for sending critical alerts across multiple channels:
 * - PagerDuty for on-call incident management
 * - Slack for team notifications
 * - Executive escalation for critical compliance/security issues
 *
 * FEATURES:
 * - Multi-channel alerting with automatic fallback
 * - Severity-based routing (critical -> PagerDuty + Slack, high -> Slack, etc.)
 * - Circuit breaker protection for each channel
 * - Metrics collection for alert delivery success/failure
 * - Context-rich alert metadata
 * - Executive escalation for compliance violations
 *
 * BUSINESS VALUE:
 * - Ensures critical issues reach the right people immediately
 * - Prevents alert fatigue through severity-based routing
 * - Maintains audit trail of all alerts sent
 * - Provides fallback mechanisms for alert delivery failures
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final PagerDutyAlertService pagerDutyAlertService;
    private final SlackNotificationService slackNotificationService;
    private final MeterRegistry meterRegistry;

    private Counter alertsSentCounter;
    private Counter alertsFailedCounter;
    private Counter pagerDutyAlertsCounter;
    private Counter slackAlertsCounter;
    private Counter executiveAlertsCounter;

    @PostConstruct
    public void initMetrics() {
        alertsSentCounter = Counter.builder("alerts.sent.total")
                .description("Total number of alerts sent across all channels")
                .tag("service", "alert-service")
                .register(meterRegistry);

        alertsFailedCounter = Counter.builder("alerts.failed.total")
                .description("Total number of failed alert deliveries")
                .tag("service", "alert-service")
                .register(meterRegistry);

        pagerDutyAlertsCounter = Counter.builder("alerts.pagerduty.total")
                .description("Total number of PagerDuty incidents created")
                .tag("service", "alert-service")
                .register(meterRegistry);

        slackAlertsCounter = Counter.builder("alerts.slack.total")
                .description("Total number of Slack notifications sent")
                .tag("service", "alert-service")
                .register(meterRegistry);

        executiveAlertsCounter = Counter.builder("alerts.executive.total")
                .description("Total number of executive escalation alerts sent")
                .tag("service", "alert-service")
                .register(meterRegistry);
    }

    /**
     * Send PagerDuty alert for critical incidents requiring immediate on-call response
     *
     * @param dedupKey Unique key for incident deduplication
     * @param summary Alert summary (max 1024 chars)
     * @param context Additional context map
     */
    @CircuitBreaker(name = "pagerduty-alerts", fallbackMethod = "pagerDutyFallback")
    public void sendPagerDutyAlert(String dedupKey, String summary, Map<String, Object> context) {
        try {
            log.warn("PAGERDUTY_ALERT: Sending PagerDuty incident - Key: {}, Summary: {}", dedupKey, summary);

            pagerDutyAlertService.triggerIncident(
                    dedupKey,
                    summary,
                    AlertSeverity.CRITICAL,
                    context
            );

            pagerDutyAlertsCounter.increment();
            alertsSentCounter.increment();

            log.info("PAGERDUTY_ALERT: Successfully sent PagerDuty incident: {}", dedupKey);

        } catch (Exception e) {
            log.error("PAGERDUTY_ALERT: Failed to send PagerDuty alert - Key: {}, Error: {}",
                    dedupKey, e.getMessage(), e);
            alertsFailedCounter.increment();
            throw e;
        }
    }

    /**
     * Fallback method for PagerDuty alert failures
     */
    private void pagerDutyFallback(String dedupKey, String summary, Map<String, Object> context, Throwable t) {
        log.error("PAGERDUTY_FALLBACK: PagerDuty circuit breaker triggered - Falling back to Slack - Key: {}, Error: {}",
                dedupKey, t.getMessage());

        try {
            // Fallback to Slack with critical severity marker
            sendSlackAlert(
                    "#ops-critical",
                    "🚨 PAGERDUTY FAILURE - " + summary + "\n\n" +
                            "PagerDuty alert failed to send. Please check PagerDuty integration.\n" +
                            "Dedup Key: " + dedupKey,
                    context
            );
        } catch (Exception slackError) {
            log.error("CRITICAL: Both PagerDuty and Slack failed for alert: {}", dedupKey, slackError);
        }
    }

    /**
     * Send Slack alert to specified channel
     *
     * @param channel Slack channel (e.g., "#compliance-critical", "#ops-alerts")
     * @param message Alert message
     * @param context Additional context map
     */
    @CircuitBreaker(name = "slack-alerts", fallbackMethod = "slackFallback")
    public void sendSlackAlert(String channel, String message, Map<String, Object> context) {
        try {
            log.info("SLACK_ALERT: Sending Slack notification - Channel: {}, Message: {}",
                    channel, truncate(message, 100));

            SlackMessage slackMessage = SlackMessage.builder()
                    .channel(channel)
                    .text(message)
                    .username("Waqiti Alert Bot")
                    .iconEmoji(":rotating_light:")
                    .attachments(buildSlackAttachments(context))
                    .build();

            slackNotificationService.sendMessage(slackMessage);

            slackAlertsCounter.increment();
            alertsSentCounter.increment();

            log.info("SLACK_ALERT: Successfully sent Slack notification to: {}", channel);

        } catch (Exception e) {
            log.error("SLACK_ALERT: Failed to send Slack alert - Channel: {}, Error: {}",
                    channel, e.getMessage(), e);
            alertsFailedCounter.increment();
            throw e;
        }
    }

    /**
     * Fallback method for Slack alert failures
     */
    private void slackFallback(String channel, String message, Map<String, Object> context, Throwable t) {
        log.error("SLACK_FALLBACK: Slack circuit breaker triggered - Logging locally - Channel: {}, Error: {}",
                channel, t.getMessage());

        // Log to application logs as last resort
        log.error("CRITICAL_ALERT_FAILED: Channel: {}, Message: {}, Context: {}", channel, message, context);
    }

    /**
     * Send executive alert for critical compliance/security incidents
     * Routes to both PagerDuty (executive escalation policy) and Slack (executive channel)
     *
     * @param title Alert title
     * @param message Detailed alert message
     * @param context Alert context
     */
    public void sendExecutiveAlert(String title, String message, Map<String, Object> context) {
        try {
            log.error("EXECUTIVE_ALERT: Sending executive escalation - Title: {}", title);

            // Send to PagerDuty with executive escalation
            String dedupKey = "executive-" + UUID.randomUUID().toString();
            sendPagerDutyAlert(dedupKey, "[EXECUTIVE] " + title, context);

            // Send to executive Slack channel
            sendSlackAlert(
                    "#executive-alerts",
                    "🚨 **EXECUTIVE ESCALATION REQUIRED**\n\n" +
                            "**Title:** " + title + "\n" +
                            "**Message:** " + message + "\n" +
                            "**Time:** " + LocalDateTime.now() + "\n" +
                            "**Requires:** Immediate executive attention",
                    context
            );

            // Also send to compliance channel if context indicates compliance issue
            if (context.containsKey("complianceViolation") &&
                Boolean.TRUE.equals(context.get("complianceViolation"))) {
                sendSlackAlert(
                        "#compliance-critical",
                        "🚨 **COMPLIANCE VIOLATION - EXECUTIVE ESCALATED**\n\n" + message,
                        context
                );
            }

            executiveAlertsCounter.increment();
            alertsSentCounter.increment();

            log.error("EXECUTIVE_ALERT: Executive alert sent successfully - Title: {}", title);

        } catch (Exception e) {
            log.error("CRITICAL: Executive alert failed - Title: {}, Error: {}",
                    title, e.getMessage(), e);
            alertsFailedCounter.increment();

            // Last resort - log to system error stream
            System.err.printf("CRITICAL EXECUTIVE ALERT FAILURE: %s - %s%n", title, message);
        }
    }

    /**
     * Send critical alert with automatic multi-channel delivery
     * Routes to PagerDuty (critical severity) + Slack (#ops-critical)
     *
     * @param title Alert title
     * @param message Alert message
     * @param context Alert context
     */
    public void sendCriticalAlert(String title, String message, Map<String, Object> context) {
        try {
            log.error("CRITICAL_ALERT: Sending critical multi-channel alert - Title: {}", title);

            // Send to PagerDuty
            String dedupKey = "critical-" + title.replaceAll("[^a-zA-Z0-9-]", "-");
            sendPagerDutyAlert(dedupKey, "[CRITICAL] " + title, context);

            // Send to Slack critical channel
            sendSlackAlert(
                    "#ops-critical",
                    "🚨 **CRITICAL ALERT**\n\n" +
                            "**Title:** " + title + "\n" +
                            "**Message:** " + message + "\n" +
                            "**Time:** " + LocalDateTime.now(),
                    context
            );

            log.info("CRITICAL_ALERT: Critical alert sent successfully - Title: {}", title);

        } catch (Exception e) {
            log.error("CRITICAL: Multi-channel critical alert failed - Title: {}, Error: {}",
                    title, e.getMessage(), e);
        }
    }

    /**
     * Send compliance alert with regulatory context
     *
     * @param title Alert title
     * @param message Alert message
     * @param context Alert context (should include compliance-specific fields)
     */
    public void sendComplianceAlert(String title, String message, Map<String, Object> context) {
        try {
            log.warn("COMPLIANCE_ALERT: Sending compliance alert - Title: {}", title);

            // Add compliance markers to context
            Map<String, Object> complianceContext = new HashMap<>(context);
            complianceContext.put("alertType", "COMPLIANCE");
            complianceContext.put("regulatoryImpact", "HIGH");

            // Send to compliance Slack channel
            sendSlackAlert(
                    "#compliance-critical",
                    "⚖️ **COMPLIANCE ALERT**\n\n" +
                            "**Title:** " + title + "\n" +
                            "**Message:** " + message + "\n" +
                            "**Time:** " + LocalDateTime.now() + "\n" +
                            "**Regulatory Impact:** HIGH",
                    complianceContext
            );

            // If critical severity, also page compliance officer
            if (context.containsKey("severity") && "CRITICAL".equals(context.get("severity"))) {
                String dedupKey = "compliance-" + title.replaceAll("[^a-zA-Z0-9-]", "-");
                sendPagerDutyAlert(dedupKey, "[COMPLIANCE] " + title, complianceContext);
            }

            alertsSentCounter.increment();

            log.info("COMPLIANCE_ALERT: Compliance alert sent successfully - Title: {}", title);

        } catch (Exception e) {
            log.error("CRITICAL: Compliance alert failed - Title: {}, Error: {}",
                    title, e.getMessage(), e);
            alertsFailedCounter.increment();
        }
    }

    /**
     * Build Slack attachments from context map
     */
    private List<SlackAttachment> buildSlackAttachments(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return Collections.emptyList();
        }

        List<SlackAttachment.Field> fields = new ArrayList<>();
        context.forEach((key, value) -> {
            if (value != null) {
                fields.add(SlackAttachment.Field.builder()
                        .title(formatFieldName(key))
                        .value(String.valueOf(value))
                        .shortField(true)
                        .build());
            }
        });

        SlackAttachment attachment = SlackAttachment.builder()
                .color("danger")
                .fields(fields)
                .footer("Waqiti Alert System")
                .timestamp(System.currentTimeMillis() / 1000)
                .build();

        return Collections.singletonList(attachment);
    }

    /**
     * Format field name for display (camelCase -> Title Case)
     */
    private String formatFieldName(String fieldName) {
        return fieldName.replaceAll("([A-Z])", " $1")
                .replaceAll("_", " ")
                .trim()
                .substring(0, 1).toUpperCase() + fieldName.substring(1);
    }

    /**
     * Truncate string for logging
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        return str.length() <= maxLength ? str : str.substring(0, maxLength) + "...";
    }
}
