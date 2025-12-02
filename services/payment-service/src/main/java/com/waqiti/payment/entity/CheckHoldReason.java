package com.waqiti.payment.entity;

/**
 * Check hold reason enumeration
 * Represents common reasons why a check deposit might be placed on hold
 */
public enum CheckHoldReason {
    // Customer-related reasons
    NEW_CUSTOMER,                    // New customer account
    FREQUENT_OVERDRAFTS,             // History of overdrafts
    DORMANT_ACCOUNT,                 // Account has been inactive
    
    // Check-related reasons
    LARGE_AMOUNT,                    // Check amount exceeds threshold
    POST_DATED,                      // Check is post-dated
    STALE_DATED,                     // Check is stale-dated (old)
    DUPLICATE_CHECK,                 // Potential duplicate deposit
    POOR_IMAGE_QUALITY,              // Image quality issues
    MICR_MISMATCH,                   // MICR data inconsistencies
    
    // Risk-related reasons
    FRAUD_SUSPECTED,                 // Fraud indicators detected
    HIGH_RISK_ACCOUNT,               // Account flagged as high risk
    VELOCITY_CHECK,                  // Too many deposits in short time
    SUSPICIOUS_ACTIVITY,             // Unusual account activity
    
    // Regulatory reasons
    COMPLIANCE_HOLD,                 // Regulatory compliance requirement
    AML_REVIEW,                      // Anti-money laundering review
    CTR_FILING,                      // Currency Transaction Report filing
    SAR_FILING,                      // Suspicious Activity Report filing
    
    // Operational reasons
    MANUAL_REVIEW_REQUIRED,          // Requires manual review
    SYSTEM_ERROR,                    // Technical issues
    BANK_VERIFICATION_PENDING,       // Awaiting bank verification
    INSUFFICIENT_ACCOUNT_HISTORY,    // Limited account history
    
    // External reasons
    PAYEE_BANK_HOLD,                 // Hold placed by paying bank
    PROCESSOR_HOLD,                  // Hold by external processor
    THIRD_PARTY_VERIFICATION,        // Third-party verification required
    
    // Other
    OTHER                            // Other unspecified reason
}