package com.waqiti.security.model;

/**
 * Authentication method enumeration
 * Defines the various authentication mechanisms supported by the system
 */
public enum AuthMethod {

    /**
     * Traditional username and password authentication
     */
    PASSWORD,

    /**
     * Biometric authentication (fingerprint, face recognition, etc.)
     */
    BIOMETRIC,

    /**
     * One-Time Password authentication
     */
    OTP,

    /**
     * Two-Factor Authentication (2FA)
     * Requires two different authentication factors
     */
    TWO_FACTOR,

    /**
     * Multi-Factor Authentication (MFA)
     * Requires multiple authentication factors (2 or more)
     */
    MFA,

    /**
     * Single Sign-On authentication
     */
    SSO,

    /**
     * API key authentication
     */
    API_KEY,

    /**
     * Certificate-based authentication
     */
    CERTIFICATE,

    /**
     * Magic link authentication (passwordless)
     */
    MAGIC_LINK;

    /**
     * Converts a string to AuthMethod enum, handling null and invalid values
     *
     * @param value the string value to convert
     * @return the corresponding AuthMethod, or null if invalid
     */
    public static AuthMethod fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            return AuthMethod.valueOf(value.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
