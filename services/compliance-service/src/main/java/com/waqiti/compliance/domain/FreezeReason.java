package com.waqiti.compliance.domain;

/**
 * Enumeration of asset freeze reasons for compliance
 */
public enum FreezeReason {
    SANCTIONS_VIOLATION("Sanctions Violation - OFAC/UN/EU"),
    LEGAL_ORDER("Legal Order"),
    COURT_ORDER("Court Order"),
    REGULATORY_REQUEST("Regulatory Request"),
    INVESTIGATION("Under Investigation"),
    AML_ALERT("Anti-Money Laundering Alert"),
    FRAUD_DETECTION("Fraud Detection"),
    KYC_FAILURE("KYC Verification Failure"),
    HIGH_RISK("High Risk Assessment"),
    POLITICAL_EXPOSURE("Politically Exposed Person");

    private final String description;

    FreezeReason(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
