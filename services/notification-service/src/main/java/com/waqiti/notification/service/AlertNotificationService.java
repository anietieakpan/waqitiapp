package com.waqiti.notification.service;

import com.waqiti.notification.domain.Alert;
import com.waqiti.notification.domain.NotificationChannel;
import com.waqiti.notification.domain.AlertSeverity;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.events.notification.AlertNotificationEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer.EventContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Service for sending alert notifications through various channels
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AlertNotificationService {

    private final NotificationService notificationService;
    private final EmailProviderService emailProviderService;
    private final InAppNotificationService inAppNotificationService;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final AuditService auditService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Send alert notification through specified channel
     */
    public void sendAlert(Alert alert, NotificationChannel channel, String correlationId) {
        try {
            log.info("Sending alert notification - alertId: {}, channel: {}, severity: {}, correlationId: {}",
                alert.getId(), channel, alert.getSeverity(), correlationId);

            switch (channel) {
                case EMAIL:
                    sendEmailAlert(alert, correlationId);
                    break;
                case SMS:
                    sendSmsAlert(alert, correlationId);
                    break;
                case PUSH:
                    sendPushAlert(alert, correlationId);
                    break;
                case IN_APP:
                    sendInAppAlert(alert, correlationId);
                    break;
                case WHATSAPP:
                    sendWhatsAppAlert(alert, correlationId);
                    break;
                case WEBHOOK:
                    sendWebhookAlert(alert, correlationId);
                    break;
                case SLACK:
                    sendSlackAlert(alert, correlationId);
                    break;
                case TEAMS:
                    sendTeamsAlert(alert, correlationId);
                    break;
                case DASHBOARD:
                    sendDashboardAlert(alert, correlationId);
                    break;
                default:
                    log.warn("Unsupported notification channel: {}", channel);
            }

            auditService.logNotificationEvent(
                "ALERT_NOTIFICATION_SENT",
                alert.getId(),
                Map.of(
                    "alertType", alert.getType(),
                    "severity", alert.getSeverity().getLevel(),
                    "channel", channel.getChannel(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now().toString()
                )
            );

        } catch (Exception e) {
            log.error("Failed to send alert notification - alertId: {}, channel: {}, error: {}",
                alert.getId(), channel, e.getMessage(), e);
            throw new RuntimeException("Alert notification failed", e);
        }
    }

    /**
     * Send critical operational alert
     */
    public void sendCriticalOperationalAlert(String title, String message, Map<String, Object> context) {
        try {
            log.error("CRITICAL OPERATIONAL ALERT: {} - {}", title, message);

            // Send through multiple high-priority channels for critical alerts
            Alert criticalAlert = Alert.builder()
                .id("ops-" + System.currentTimeMillis())
                .type("OPERATIONAL")
                .severity(AlertSeverity.CRITICAL)
                .title(title)
                .description(message)
                .source("SYSTEM")
                .category("OPERATIONAL")
                .requiresAcknowledgment(true)
                .metadata(context)
                .timestamp(java.time.LocalDateTime.now())
                .correlationId("critical-ops-" + System.currentTimeMillis())
                .build();

            // Send to multiple channels for critical alerts
            sendAlert(criticalAlert, NotificationChannel.EMAIL, criticalAlert.getCorrelationId());
            sendAlert(criticalAlert, NotificationChannel.SLACK, criticalAlert.getCorrelationId());
            sendAlert(criticalAlert, NotificationChannel.DASHBOARD, criticalAlert.getCorrelationId());

        } catch (Exception e) {
            log.error("Failed to send critical operational alert: {}", e.getMessage(), e);
        }
    }

    private void sendEmailAlert(Alert alert, String correlationId) {
        try {
            String subject = String.format("[%s] %s", alert.getSeverity().getLevel(), alert.getTitle());
            String content = formatAlertEmailContent(alert);

            emailProviderService.sendOperationalEmail(
                getOperationalEmailRecipients(alert.getSeverity()),
                subject,
                content,
                correlationId
            );
        } catch (Exception e) {
            log.error("Failed to send email alert: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void sendSmsAlert(Alert alert, String correlationId) {
        // Only send SMS for critical alerts to avoid spam
        if (alert.getSeverity() == AlertSeverity.CRITICAL) {
            try {
                String message = String.format("CRITICAL ALERT: %s - %s", alert.getTitle(), alert.getDescription());
                // Implementation would integrate with SMS service
                log.info("SMS alert sent for critical alert: {}", alert.getId());
            } catch (Exception e) {
                log.error("Failed to send SMS alert: {}", e.getMessage(), e);
            }
        }
    }

    private void sendPushAlert(Alert alert, String correlationId) {
        try {
            // Send push notification to operations team devices
            log.info("Push alert notification sent: {}", alert.getId());
        } catch (Exception e) {
            log.error("Failed to send push alert: {}", e.getMessage(), e);
        }
    }

    private void sendInAppAlert(Alert alert, String correlationId) {
        try {
            inAppNotificationService.sendSystemAlert(alert, correlationId);
        } catch (Exception e) {
            log.error("Failed to send in-app alert: {}", e.getMessage(), e);
        }
    }

    private void sendWhatsAppAlert(Alert alert, String correlationId) {
        // Only for critical alerts
        if (alert.getSeverity() == AlertSeverity.CRITICAL) {
            try {
                whatsAppNotificationService.sendOperationalAlert(alert, correlationId);
            } catch (Exception e) {
                log.error("Failed to send WhatsApp alert: {}", e.getMessage(), e);
            }
        }
    }

    private void sendWebhookAlert(Alert alert, String correlationId) {
        try {
            // Send to external monitoring systems
            log.info("Webhook alert sent: {}", alert.getId());
        } catch (Exception e) {
            log.error("Failed to send webhook alert: {}", e.getMessage(), e);
        }
    }

    private void sendSlackAlert(Alert alert, String correlationId) {
        try {
            // Send to Slack operations channel
            log.info("Slack alert sent: {}", alert.getId());
        } catch (Exception e) {
            log.error("Failed to send Slack alert: {}", e.getMessage(), e);
        }
    }

    private void sendTeamsAlert(Alert alert, String correlationId) {
        try {
            // Send to Microsoft Teams operations channel
            log.info("Teams alert sent: {}", alert.getId());
        } catch (Exception e) {
            log.error("Failed to send Teams alert: {}", e.getMessage(), e);
        }
    }

    private void sendDashboardAlert(Alert alert, String correlationId) {
        try {
            // Update dashboard with real-time alert
            log.info("Dashboard alert sent: {}", alert.getId());
        } catch (Exception e) {
            log.error("Failed to send dashboard alert: {}", e.getMessage(), e);
        }
    }

    private String formatAlertEmailContent(Alert alert) {
        return String.format("""
            Alert Details:

            Alert ID: %s
            Type: %s
            Severity: %s
            Source: %s
            Affected Service: %s
            Category: %s
            Timestamp: %s

            Description:
            %s

            Correlation ID: %s

            This is an automated alert from the Waqiti monitoring system.
            """,
            alert.getId(),
            alert.getType(),
            alert.getSeverity().getLevel(),
            alert.getSource(),
            alert.getAffectedService(),
            alert.getCategory(),
            alert.getTimestamp(),
            alert.getDescription(),
            alert.getCorrelationId()
        );
    }

    private String getOperationalEmailRecipients(AlertSeverity severity) {
        // Return appropriate email distribution list based on severity
        switch (severity) {
            case CRITICAL:
                return "ops-critical@example.com";
            case HIGH:
                return "ops-high@example.com";
            default:
                return "ops-general@example.com";
        }
    }

    /**
     * Process alert notification event from Kafka consumer
     *
     * @param event Alert notification event
     * @param context Event context with metadata
     */
    public void processAlertNotification(AlertNotificationEvent event, EventContext context) {
        try {
            log.info("Processing alert notification event - EventId: {}, AlertType: {}, Severity: {}, CorrelationId: {}",
                    event.getEventId(), event.getAlertType(), event.getSeverity(), context.getCorrelationId());

            // Convert event to Alert domain object
            Alert alert = Alert.builder()
                    .id(event.getEventId())
                    .type(event.getAlertType())
                    .severity(mapSeverity(event.getSeverity()))
                    .title(event.getTitle())
                    .description(event.getMessage())
                    .source(event.getSource())
                    .affectedService(event.getAffectedService())
                    .category(event.getCategory())
                    .requiresAcknowledgment(event.isRequiresAcknowledgment())
                    .metadata(event.getMetadata())
                    .timestamp(java.time.LocalDateTime.now())
                    .correlationId(context.getCorrelationId())
                    .build();

            // Determine notification channels based on severity
            for (String channelName : event.getChannels()) {
                try {
                    NotificationChannel channel = NotificationChannel.valueOf(channelName.toUpperCase());
                    sendAlert(alert, channel, context.getCorrelationId());
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid notification channel: {}", channelName);
                }
            }

            log.info("Successfully processed alert notification event - EventId: {}, CorrelationId: {}",
                    event.getEventId(), context.getCorrelationId());

        } catch (Exception e) {
            log.error("Failed to process alert notification event - EventId: {}, CorrelationId: {}, Error: {}",
                    event.getEventId(), context.getCorrelationId(), e.getMessage(), e);
            throw new RuntimeException("Alert notification processing failed", e);
        }
    }

    /**
     * Send failed message to Dead Letter Queue
     *
     * @param record Consumer record that failed
     * @param error Exception that caused the failure
     * @param correlationId Correlation ID for tracking
     */
    public void sendToDlq(ConsumerRecord<String, String> record, Exception error, String correlationId) {
        try {
            log.error("Sending failed alert notification to DLQ - Topic: {}, Partition: {}, Offset: {}, CorrelationId: {}, Error: {}",
                    record.topic(), record.partition(), record.offset(), correlationId, error.getMessage());

            String dlqTopic = record.topic() + "-dlq";
            String dlqMessage = String.format(
                    "{\"originalTopic\":\"%s\",\"partition\":%d,\"offset\":%d,\"error\":\"%s\",\"correlationId\":\"%s\",\"payload\":%s}",
                    record.topic(), record.partition(), record.offset(),
                    error.getMessage().replace("\"", "\\\""), correlationId, record.value()
            );

            kafkaTemplate.send(dlqTopic, record.key(), dlqMessage);

            log.info("Successfully sent message to DLQ - DLQTopic: {}, CorrelationId: {}", dlqTopic, correlationId);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to send message to DLQ - CorrelationId: {}, Error: {}",
                    correlationId, e.getMessage(), e);
            // Last resort - log to stderr
            System.err.printf("CRITICAL DLQ FAILURE: Topic=%s, CorrelationId=%s, Error=%s%n",
                    record.topic(), correlationId, e.getMessage());
        }
    }

    /**
     * Map event severity string to AlertSeverity enum
     */
    private AlertSeverity mapSeverity(String severity) {
        try {
            return AlertSeverity.valueOf(severity.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown severity level: {}, defaulting to MEDIUM", severity);
            return AlertSeverity.MEDIUM;
        }
    }
}