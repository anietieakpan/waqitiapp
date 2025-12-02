package com.waqiti.common.notification.sms.dto;

/**
 * Reasons for SMS delivery failure
 */
public enum SmsFailureReason {
    INVALID_PHONE_NUMBER,
    RATE_LIMITED,
    COMPLIANCE_VIOLATION,
    NETWORK_ERROR,
    PROVIDER_ERROR,
    INSUFFICIENT_BALANCE,
    BLOCKED_NUMBER,
    OPTED_OUT,
    TIMEOUT,
    INVALID_CONTENT,
    CARRIER_BLOCKED,
    COUNTRY_NOT_SUPPORTED,
    DUPLICATE_MESSAGE,
    SYSTEM_ERROR,
    UNKNOWN
}