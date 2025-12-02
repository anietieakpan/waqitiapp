package com.waqiti.common.fraud.model;

/**
 * Types of fraud patterns that can be detected
 */
public enum FraudPatternType {
    CARD_TESTING,           // Testing stolen card numbers with small transactions
    ACCOUNT_TAKEOVER,       // Unauthorized access to existing accounts
    MONEY_LAUNDERING,       // Suspicious transaction patterns for laundering
    SYNTHETIC_IDENTITY,     // Use of fake/synthetic identities
    PAYMENT_FRAUD,          // Fraudulent payment attempts
    VELOCITY_ABUSE,         // High-velocity transaction patterns
    GEOGRAPHIC_ANOMALY,     // Unusual geographic transaction patterns
    DEVICE_FRAUD,           // Device-based fraud patterns
    BEHAVIORAL_ANOMALY,     // Unusual user behavior patterns
    MERCHANT_FRAUD,         // Fraudulent merchant activities
    REFUND_ABUSE,           // Abuse of refund processes
    CHARGEBACK_FRAUD,       // Fraudulent chargeback patterns
    IDENTITY_THEFT,         // Stolen identity usage patterns
    BUSINESS_FRAUD,         // Fraudulent business account activities
    CRYPTOCURRENCY_FRAUD,   // Crypto-related fraud patterns
    CROSS_BORDER_FRAUD,     // International fraud patterns
    DORMANT_ACCOUNT_ABUSE,  // Abuse of inactive accounts
    PROMOTIONAL_ABUSE,      // Abuse of promotional offers
    API_ABUSE,              // Fraudulent API usage patterns
    SOCIAL_ENGINEERING      // Social engineering attack patterns
}