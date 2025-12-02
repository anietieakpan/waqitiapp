package com.waqiti.compliance.aml.dto;

/**
 * Types of AML alerts
 */
public enum AMLAlertType {
    STRUCTURING,              // Multiple transactions just under $10k
    RAPID_MOVEMENT,           // Fast money in/out
    HIGH_VELOCITY,            // Too many transactions in short time
    UNUSUAL_PATTERN,          // Deviates from normal behavior
    HIGH_RISK_JURISDICTION,   // Transactions to/from high-risk countries
    ROUND_DOLLAR_AMOUNTS,     // Suspicious round amounts
    SMURFING,                 // Multiple small transactions across accounts
    LAYERING,                 // Complex transaction chains
    CTR_AVOIDANCE,            // Pattern of staying under CTR threshold
    DORMANT_ACCOUNT_ACTIVITY  // Sudden activity on dormant account
}
