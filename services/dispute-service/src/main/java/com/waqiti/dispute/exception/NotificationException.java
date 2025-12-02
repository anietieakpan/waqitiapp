package com.waqiti.dispute.exception;

/**
 * Exception thrown when notification sending fails
 *
 * This is a non-critical exception - notification failures should not block dispute processing
 *
 * HTTP Status: 500 Internal Server Error
 *
 * @author Waqiti Dispute Team
 */
public class NotificationException extends DisputeServiceException {

    private final String notificationType;
    private final String recipient;

    public NotificationException(String notificationType, String recipient, String message) {
        super(String.format("Failed to send %s notification to %s: %s", notificationType, recipient, message),
                "NOTIFICATION_ERROR", 500);
        this.notificationType = notificationType;
        this.recipient = recipient;
    }

    public NotificationException(String notificationType, String recipient, String message, Throwable cause) {
        super(String.format("Failed to send %s notification to %s: %s", notificationType, recipient, message),
                cause, "NOTIFICATION_ERROR", 500);
        this.notificationType = notificationType;
        this.recipient = recipient;
    }

    public String getNotificationType() {
        return notificationType;
    }

    public String getRecipient() {
        return recipient;
    }
}
