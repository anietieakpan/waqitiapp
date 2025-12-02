package com.waqiti.common.exception;

/**
 * Exception thrown when SMS provider (Twilio, etc.) fails.
 *
 * Common causes:
 * - Twilio API down
 * - Invalid API credentials
 * - Rate limiting exceeded
 * - Invalid phone number format
 * - Insufficient balance
 * - Carrier blocking
 *
 * This should trigger:
 * - Retry for transient errors
 * - Failover to backup SMS provider
 * - Queue for later delivery
 * - Alert operations team
 * - Log for compliance audit
 *
 * @author Waqiti Platform
 */
public class SmsProviderException extends NotificationDeliveryException {

    private final String phoneNumber;
    private final String smsProvider;
    private final String errorCode;

    /**
     * Creates exception with message
     */
    public SmsProviderException(String message) {
        super(message);
        this.phoneNumber = null;
        this.smsProvider = null;
        this.errorCode = null;
    }

    /**
     * Creates exception with message and cause
     */
    public SmsProviderException(String message, Throwable cause) {
        super(message, cause);
        this.phoneNumber = null;
        this.smsProvider = null;
        this.errorCode = null;
    }

    /**
     * Creates exception with detailed SMS information
     */
    public SmsProviderException(String message, String phoneNumber, String smsProvider,
                               String errorCode, boolean retryable) {
        super(message, "SMS", phoneNumber, smsProvider, retryable);
        this.phoneNumber = phoneNumber;
        this.smsProvider = smsProvider;
        this.errorCode = errorCode;
    }

    /**
     * Creates exception with detailed SMS information and cause
     */
    public SmsProviderException(String message, String phoneNumber, String smsProvider,
                               String errorCode, boolean retryable, Throwable cause) {
        super(message, "SMS", phoneNumber, smsProvider, retryable, cause);
        this.phoneNumber = phoneNumber;
        this.smsProvider = smsProvider;
        this.errorCode = errorCode;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getSmsProvider() {
        return smsProvider;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
