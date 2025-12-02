package com.waqiti.security.domain;

/**
 * Enumeration of AML alert types for compliance monitoring
 */
public enum AmlAlertType {
    // Transaction-based alerts
    LARGE_CASH_TRANSACTION,
    STRUCTURING,
    DAILY_THRESHOLD_EXCEEDED,
    WEEKLY_THRESHOLD_EXCEEDED,
    ROUND_NUMBER_TRANSACTION,
    HIGH_VELOCITY,
    RAPID_SUCCESSION,
    
    // Pattern-based alerts
    CIRCULAR_TRANSACTION,
    UNUSUAL_PAYMENT_PATTERN,
    DORMANT_ACCOUNT_REACTIVATION,
    GEOGRAPHIC_ANOMALY,
    TIME_ANOMALY,
    
    // Risk-based alerts
    HIGH_RISK_JURISDICTION,
    SANCTIONS_SCREENING_MATCH,
    PEP_TRANSACTION, // Politically Exposed Person
    ADVERSE_MEDIA_MATCH,
    
    // Behavioral alerts
    OFF_HOURS_TRANSACTION,
    MERCHANT_LIKE_ACTIVITY,
    ACCOUNT_TAKEOVER_INDICATOR,
    IDENTITY_VERIFICATION_FAILURE,
    
    // System alerts
    SYSTEM_ERROR,
    MONITORING_FAILURE,
    DATA_QUALITY_ISSUE,
    
    // Investigation alerts
    MANUAL_REVIEW_REQUIRED,
    ENHANCED_DUE_DILIGENCE,
    COMPLIANCE_REVIEW
}