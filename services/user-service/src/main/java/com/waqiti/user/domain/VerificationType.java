package com.waqiti.user.domain;

/**
 * Represents the possible verification methods
 */
public enum VerificationType {
    EMAIL,      // Email verification
    PHONE,      // Phone verification
    KYC_BASIC,  // Basic KYC verification
    KYC_FULL,    // Full KYC verification
    PASSWORD_RESET
    }