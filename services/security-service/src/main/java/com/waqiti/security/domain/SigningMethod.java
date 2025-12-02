package com.waqiti.security.domain;

public enum SigningMethod {
    SOFTWARE_KEY("Software Key", "Standard software-based cryptographic signing"),
    HARDWARE_KEY("Hardware Key", "Hardware security module or device-based signing"),
    BIOMETRIC("Biometric", "Biometric authentication-based signing"),
    MULTI_SIGNATURE("Multi-Signature", "Multiple signatures required"),
    SMS_OTP("SMS OTP", "SMS one-time password verification"),
    TOTP("TOTP", "Time-based one-time password"),
    PUSH_NOTIFICATION("Push Notification", "Mobile push notification approval"),
    VOICE_VERIFICATION("Voice Verification", "Voice biometric verification"),
    BEHAVIORAL("Behavioral", "Behavioral biometric patterns");

    private final String displayName;
    private final String description;

    SigningMethod(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isHardwareBased() {
        return this == HARDWARE_KEY;
    }

    public boolean isBiometricBased() {
        return this == BIOMETRIC || this == VOICE_VERIFICATION || this == BEHAVIORAL;
    }

    public boolean requiresMultipleParties() {
        return this == MULTI_SIGNATURE;
    }

    public boolean isOutOfBand() {
        return this == SMS_OTP || this == PUSH_NOTIFICATION || this == VOICE_VERIFICATION;
    }

    public int getSecurityLevel() {
        switch (this) {
            case HARDWARE_KEY:
            case MULTI_SIGNATURE:
                return 5; // Highest
            case BIOMETRIC:
            case BEHAVIORAL:
                return 4;
            case SOFTWARE_KEY:
            case PUSH_NOTIFICATION:
                return 3;
            case TOTP:
            case VOICE_VERIFICATION:
                return 2;
            case SMS_OTP:
                return 1; // Lowest
            default:
                return 0;
        }
    }
}