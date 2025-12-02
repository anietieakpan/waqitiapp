package com.waqiti.insurance.model;

/**
 * Reasons for halting claim processing
 */
public enum HaltReason {
    REGULATORY_VIOLATION,
    FRAUD_SUSPECTED,
    COMPLIANCE_FAILURE,
    SYSTEM_ERROR,
    MANUAL_REVIEW_REQUIRED,
    CUSTOMER_PROTECTION,
    LEGAL_HOLD
}
