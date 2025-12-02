package com.waqiti.common.exception;

/**
 * Exception thrown when email provider (SendGrid, etc.) fails.
 *
 * Common causes:
 * - SendGrid API down
 * - Invalid API credentials
 * - Rate limiting exceeded
 * - Invalid email format
 * - Recipient blocked/bounced
 * - Spam filtering
 * - Attachment size exceeded
 *
 * This should trigger:
 * - Retry for transient errors
 * - Failover to backup email provider
 * - Queue for later delivery
 * - Alert operations team
 * - Log for compliance audit
 *
 * @author Waqiti Platform
 */
public class EmailProviderException extends NotificationDeliveryException {

    private final String emailAddress;
    private final String emailProvider;
    private final String errorCode;
    private final String subject;

    /**
     * Creates exception with message
     */
    public EmailProviderException(String message) {
        super(message);
        this.emailAddress = null;
        this.emailProvider = null;
        this.errorCode = null;
        this.subject = null;
    }

    /**
     * Creates exception with message and cause
     */
    public EmailProviderException(String message, Throwable cause) {
        super(message, cause);
        this.emailAddress = null;
        this.emailProvider = null;
        this.errorCode = null;
        this.subject = null;
    }

    /**
     * Creates exception with detailed email information
     */
    public EmailProviderException(String message, String emailAddress, String emailProvider,
                                 String errorCode, String subject, boolean retryable) {
        super(message, "EMAIL", emailAddress, emailProvider, retryable);
        this.emailAddress = emailAddress;
        this.emailProvider = emailProvider;
        this.errorCode = errorCode;
        this.subject = subject;
    }

    /**
     * Creates exception with detailed email information and cause
     */
    public EmailProviderException(String message, String emailAddress, String emailProvider,
                                 String errorCode, String subject, boolean retryable, Throwable cause) {
        super(message, "EMAIL", emailAddress, emailProvider, retryable, cause);
        this.emailAddress = emailAddress;
        this.emailProvider = emailProvider;
        this.errorCode = errorCode;
        this.subject = subject;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public String getEmailProvider() {
        return emailProvider;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getSubject() {
        return subject;
    }
}
