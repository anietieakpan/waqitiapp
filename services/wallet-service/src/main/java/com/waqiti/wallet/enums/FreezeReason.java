package com.waqiti.wallet.enums;

/**
 * Detailed reason for wallet freeze.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-10-18
 */
public enum FreezeReason {
    // Fraud-related
    FRAUD_DETECTED("Fraudulent activity detected"),
    FRAUD_SUSPECTED("Suspected fraudulent activity"),
    UNUSUAL_ACTIVITY("Unusual transaction patterns"),
    VELOCITY_BREACH("Transaction velocity limits exceeded"),
    AMOUNT_ANOMALY("Abnormal transaction amounts"),

    // Security-related
    ACCOUNT_COMPROMISED("Account security compromised"),
    UNAUTHORIZED_ACCESS("Unauthorized access detected"),
    PASSWORD_BREACH("Password breach detected"),
    DEVICE_FRAUD("Fraudulent device activity"),
    SESSION_HIJACKING("Session hijacking suspected"),

    // Compliance-related
    KYC_INCOMPLETE("KYC verification incomplete"),
    KYC_EXPIRED("KYC documentation expired"),
    AML_ALERT("AML screening alert"),
    SANCTIONS_MATCH("Sanctions list match"),
    PEP_SCREENING("PEP screening required"),
    HIGH_RISK_COUNTRY("High-risk country transaction"),

    // Legal/Regulatory
    COURT_ORDER_FREEZE("Court-ordered freeze"),
    LAW_ENFORCEMENT_REQUEST("Law enforcement request"),
    REGULATORY_HOLD("Regulatory hold"),
    SUBPOENA("Subpoena received"),
    BANKRUPTCY("Bankruptcy filing"),

    // Customer-initiated
    CUSTOMER_REQUEST_LOST_DEVICE("Customer reported lost device"),
    CUSTOMER_REQUEST_SECURITY("Customer security concern"),
    CUSTOMER_REQUEST_TEMPORARY("Customer requested temporary freeze"),

    // Operational
    DISPUTE_INVESTIGATION("Chargeback/dispute under investigation"),
    NEGATIVE_BALANCE("Negative balance detected"),
    DEBT_COLLECTION("Debt collection action"),
    ACCOUNT_CLOSURE_PENDING("Account closure in progress"),
    SYSTEM_MAINTENANCE("System maintenance"),

    // Risk-based
    HIGH_RISK_BEHAVIOR("High-risk behavioral pattern"),
    GEOGRAPHIC_ANOMALY("Geographic location anomaly"),
    MULTIPLE_FAILED_ATTEMPTS("Multiple failed transaction attempts"),
    BLACKLIST_MATCH("Blacklist match"),

    // Other
    MANUAL_REVIEW_REQUIRED("Manual review required"),
    SYSTEM_ERROR("System error - protective freeze"),
    UNKNOWN("Unknown reason");

    private final String description;

    FreezeReason(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Parse string to FreezeReason enum
     */
    public static FreezeReason fromString(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            return UNKNOWN;
        }

        // Try exact match first
        try {
            return FreezeReason.valueOf(reason.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            // Try matching by description
            for (FreezeReason fr : values()) {
                if (fr.description.equalsIgnoreCase(reason)) {
                    return fr;
                }
            }
            return UNKNOWN;
        }
    }

    /**
     * Check if this reason is related to fraud
     */
    public boolean isFraudRelated() {
        return this == FRAUD_DETECTED || this == FRAUD_SUSPECTED ||
               this == UNUSUAL_ACTIVITY || this == VELOCITY_BREACH ||
               this == AMOUNT_ANOMALY;
    }

    /**
     * Check if this reason is related to compliance
     */
    public boolean isComplianceRelated() {
        return this == KYC_INCOMPLETE || this == KYC_EXPIRED ||
               this == AML_ALERT || this == SANCTIONS_MATCH ||
               this == PEP_SCREENING || this == HIGH_RISK_COUNTRY;
    }

    /**
     * Check if this reason is legal/regulatory
     */
    public boolean isLegalRelated() {
        return this == COURT_ORDER_FREEZE || this == LAW_ENFORCEMENT_REQUEST ||
               this == REGULATORY_HOLD || this == SUBPOENA ||
               this == BANKRUPTCY;
    }

    /**
     * Check if this requires urgent action
     */
    public boolean isUrgent() {
        return this == FRAUD_DETECTED || this == ACCOUNT_COMPROMISED ||
               this == UNAUTHORIZED_ACCESS || this == SANCTIONS_MATCH ||
               this == LAW_ENFORCEMENT_REQUEST;
    }
}
