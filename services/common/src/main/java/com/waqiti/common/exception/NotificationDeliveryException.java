package com.waqiti.common.exception;

/**
 * Exception thrown when notification delivery fails.
 *
 * Common causes:
 * - SMS provider down (Twilio)
 * - Email provider down (SendGrid)
 * - Push notification service unavailable (FCM)
 * - Invalid recipient details
 * - Rate limiting
 *
 * This should trigger:
 * - Retry with exponential backoff
 * - Failover to backup provider
 * - Queue for later delivery
 * - Alert operations team
 * - Log for compliance
 *
 * @author Waqiti Platform
 */
public class NotificationDeliveryException extends RuntimeException {

    private final String notificationType; // SMS, EMAIL, PUSH
    private final String recipient;
    private final String provider;
    private final boolean retryable;

    /**
     * Creates exception with message
     */
    public NotificationDeliveryException(String message) {
        super(message);
        this.notificationType = null;
        this.recipient = null;
        this.provider = null;
        this.retryable = true;
    }

    /**
     * Creates exception with message and cause
     */
    public NotificationDeliveryException(String message, Throwable cause) {
        super(message, cause);
        this.notificationType = null;
        this.recipient = null;
        this.provider = null;
        this.retryable = true;
    }

    /**
     * Creates exception with detailed notification information
     */
    public NotificationDeliveryException(String message, String notificationType, String recipient,
                                        String provider, boolean retryable) {
        super(String.format("%s (type=%s, recipient=%s, provider=%s, retryable=%s)",
            message, notificationType, maskRecipient(recipient), provider, retryable));
        this.notificationType = notificationType;
        this.recipient = recipient;
        this.provider = provider;
        this.retryable = retryable;
    }

    /**
     * Creates exception with detailed notification information and cause
     */
    public NotificationDeliveryException(String message, String notificationType, String recipient,
                                        String provider, boolean retryable, Throwable cause) {
        super(String.format("%s (type=%s, recipient=%s, provider=%s, retryable=%s)",
            message, notificationType, maskRecipient(recipient), provider, retryable), cause);
        this.notificationType = notificationType;
        this.recipient = recipient;
        this.provider = provider;
        this.retryable = retryable;
    }

    private static String maskRecipient(String recipient) {
        if (recipient == null || recipient.length() < 4) {
            return "***";
        }
        return recipient.substring(0, 2) + "***" + recipient.substring(recipient.length() - 2);
    }

    public String getNotificationType() {
        return notificationType;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getProvider() {
        return provider;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
