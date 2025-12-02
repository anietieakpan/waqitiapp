package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Standard general-purpose notification request
 *
 * This is the concrete implementation of NotificationRequest for scenarios
 * where specialized notification types (Email, SMS, Push, etc.) are not needed.
 *
 * Use this for:
 * - Internal system notifications
 * - Generic alerts that route to multiple channels
 * - Fraud alerts and security notifications
 * - Any notification where the channel is determined by routing logic
 *
 * PRODUCTION FIX: Created to support builder() pattern on NotificationRequest
 * while maintaining the abstract base class design for specialized types.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class StandardNotificationRequest extends NotificationRequest {

    /**
     * Recipient identifier (user ID, email, phone, or routing key)
     * The interpretation depends on the notification channel
     */
    private String recipient;

    /**
     * Notification subject/title
     */
    private String subject;

    /**
     * Notification message body
     */
    private String message;

    /**
     * Optional structured payload for complex notifications
     * Can contain rich content, attachments, actions, etc.
     */
    private Object payload;

    /**
     * Template ID if this notification uses a template
     * When set, message may contain template variables
     */
    private String templateId;

    /**
     * Notification category for routing and filtering
     * Examples: SECURITY, FRAUD, TRANSACTION, ACCOUNT, MARKETING
     */
    private String category;

    /**
     * Action buttons or links for interactive notifications
     * Format: Map of action ID to action config
     */
    private java.util.Map<String, NotificationAction> actions;

    /**
     * Notification action configuration
     */
    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationAction {
        private String label;
        private String url;
        private String actionType; // BUTTON, LINK, INLINE_REPLY, etc.
        private java.util.Map<String, Object> parameters;
    }

    /**
     * Get the effective notification title
     * @return subject if set, otherwise derives from message
     */
    public String getEffectiveTitle() {
        if (subject != null && !subject.trim().isEmpty()) {
            return subject;
        }
        // Derive title from first line of message
        if (message != null) {
            String[] lines = message.split("\n", 2);
            return lines[0].substring(0, Math.min(50, lines[0].length()));
        }
        return "Notification";
    }

    /**
     * Check if this notification has interactive actions
     * @return true if actions are configured
     */
    public boolean hasActions() {
        return actions != null && !actions.isEmpty();
    }

    /**
     * Check if this notification uses a template
     * @return true if templateId is set
     */
    public boolean isTemplated() {
        return templateId != null && !templateId.trim().isEmpty();
    }

    /**
     * Validate the notification request
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        // Must have recipient
        if (recipient == null || recipient.trim().isEmpty()) {
            return false;
        }

        // Must have either message or template
        boolean hasMessage = message != null && !message.trim().isEmpty();
        boolean hasTemplate = templateId != null && !templateId.trim().isEmpty();

        return hasMessage || hasTemplate || payload != null;
    }
}
