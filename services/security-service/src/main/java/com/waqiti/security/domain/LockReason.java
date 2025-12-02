package com.waqiti.security.domain;

/**
 * Lock Reason Enum
 *
 * Defines the reasons for account locks with associated severity levels.
 */
public enum LockReason {
    /**
     * Too many failed login attempts
     */
    FAILED_LOGIN_ATTEMPTS(3),

    /**
     * Suspicious activity detected
     */
    SUSPICIOUS_ACTIVITY(7),

    /**
     * Fraud detected on account
     */
    FRAUD_DETECTED(10),

    /**
     * Compliance requirements mandate lock
     */
    COMPLIANCE_REQUIRED(9),

    /**
     * User requested account lock
     */
    USER_REQUEST(2),

    /**
     * Credentials have been compromised
     */
    CREDENTIAL_COMPROMISE(9),

    /**
     * Rate limit exceeded
     */
    RATE_LIMIT_EXCEEDED(4),

    /**
     * Account takeover suspected
     */
    ACCOUNT_TAKEOVER_SUSPECTED(10),

    /**
     * Regulatory investigation
     */
    REGULATORY_INVESTIGATION(8),

    /**
     * Payment dispute or chargeback
     */
    PAYMENT_DISPUTE(6),

    /**
     * AML (Anti-Money Laundering) concerns
     */
    AML_CONCERN(9),

    /**
     * Sanctions screening hit
     */
    SANCTIONS_HIT(10),

    /**
     * Administrative lock by support team
     */
    ADMINISTRATIVE(5),

    /**
     * Technical security issue
     */
    TECHNICAL_SECURITY_ISSUE(7),

    /**
     * Other/unspecified reason
     */
    OTHER(1);

    private final int severity;

    LockReason(int severity) {
        this.severity = severity;
    }

    /**
     * Get the severity level of this lock reason (1-10, 10 being most severe)
     */
    public int getSeverity() {
        return severity;
    }

    /**
     * Check if this is a high-severity lock reason
     */
    public boolean isHighSeverity() {
        return severity >= 8;
    }

    /**
     * Check if this is a critical lock reason requiring immediate action
     */
    public boolean isCritical() {
        return severity >= 9;
    }
}
