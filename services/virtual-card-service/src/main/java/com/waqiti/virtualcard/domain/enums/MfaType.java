package com.waqiti.virtualcard.domain.enums;

/**
 * Multi-Factor Authentication Types
 */
public enum MfaType {
    /**
     * Time-based One-Time Password (Google Authenticator, Authy, etc.)
     */
    TOTP,

    /**
     * Device biometric authentication (fingerprint, face ID)
     */
    DEVICE_BIOMETRIC,

    /**
     * SMS-based One-Time Password
     */
    SMS_OTP,

    /**
     * Email-based One-Time Password
     */
    EMAIL_OTP,

    /**
     * Hardware security key (YubiKey, etc.)
     */
    HARDWARE_TOKEN,

    /**
     * Push notification approval
     */
    PUSH_NOTIFICATION
}
