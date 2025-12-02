package com.waqiti.analytics.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Notification Request DTO
 *
 * Request object for sending notifications through notification-service.
 * Supports multiple channels and recipients.
 *
 * @author Waqiti Analytics Team
 * @version 1.0.0-PRODUCTION
 * @since 2025-11-15
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationRequest {

    /**
     * Correlation ID for distributed tracing
     */
    private String correlationId;

    /**
     * Notification type (ALERT_RESOLVED, ESCALATION, FAILURE, DLQ_ALERT, etc.)
     */
    private NotificationType type;

    /**
     * Priority level (LOW, MEDIUM, HIGH, CRITICAL)
     */
    private NotificationPriority priority;

    /**
     * List of recipient user IDs or email addresses
     */
    private List<String> recipients;

    /**
     * Delivery channels (EMAIL, SMS, PUSH, IN_APP)
     */
    private List<NotificationChannel> channels;

    /**
     * Notification subject/title
     */
    private String subject;

    /**
     * Notification body/message
     */
    private String message;

    /**
     * Additional metadata for the notification
     */
    private Map<String, Object> metadata;

    /**
     * Template ID if using a predefined template
     */
    private String templateId;

    /**
     * Template variables for rendering
     */
    private Map<String, String> templateVariables;

    public enum NotificationType {
        ALERT_RESOLVED,
        ESCALATION,
        FAILURE,
        DLQ_ALERT,
        MANUAL_REVIEW,
        CRITICAL_ALERT,
        SYSTEM_STATUS
    }

    public enum NotificationPriority {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public enum NotificationChannel {
        EMAIL,
        SMS,
        PUSH,
        IN_APP,
        WEBHOOK
    }
}
