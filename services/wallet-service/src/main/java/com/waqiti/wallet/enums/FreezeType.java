package com.waqiti.wallet.enums;

/**
 * Type of wallet freeze operation.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-10-18
 */
public enum FreezeType {
    /**
     * Freeze due to detected or suspected fraud
     */
    FRAUD_PREVENTION,

    /**
     * Freeze for compliance or regulatory requirements
     */
    COMPLIANCE,

    /**
     * Freeze due to security concerns (compromised account, suspicious activity)
     */
    SECURITY,

    /**
     * Administrative freeze by operations team
     */
    ADMINISTRATIVE,

    /**
     * Court-ordered freeze
     */
    COURT_ORDER,

    /**
     * Suspicious activity detected by ML/AI systems
     */
    SUSPICIOUS_ACTIVITY,

    /**
     * Customer-initiated freeze (lost phone, etc.)
     */
    CUSTOMER_REQUESTED,

    /**
     * AML/KYC verification pending
     */
    KYC_PENDING,

    /**
     * Sanctions screening match
     */
    SANCTIONS,

    /**
     * Chargeback or dispute investigation
     */
    DISPUTE_INVESTIGATION,

    /**
     * Temporary freeze during system maintenance
     */
    SYSTEM_MAINTENANCE
}
