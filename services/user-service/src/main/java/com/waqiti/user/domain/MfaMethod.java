// File: services/user-service/src/main/java/com/waqiti/user/domain/MfaMethod.java
package com.waqiti.user.domain;

/**
 * Represents the different methods available for multi-factor authentication
 */
public enum MfaMethod {
    TOTP,   // Time-based One-Time Password (authenticator apps)
    SMS,    // SMS verification codes
    EMAIL,   // Email verification codes
    RECOVERY_CODE  // Recovery codes for account access when other methods fail
}