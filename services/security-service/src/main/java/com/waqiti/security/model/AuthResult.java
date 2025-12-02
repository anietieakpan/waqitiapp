package com.waqiti.security.model;

/**
 * Authentication Result Enum
 * Represents the outcome of an authentication attempt
 */
public enum AuthResult {
    SUCCESS,
    FAILED,
    BLOCKED,
    PENDING,
    ERROR,
    EXPIRED,
    LOCKED,
    CHALLENGED;

    public static AuthResult fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return AuthResult.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return FAILED; // Default fallback
        }
    }
}
