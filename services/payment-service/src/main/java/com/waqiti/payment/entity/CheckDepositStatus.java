package com.waqiti.payment.entity;

/**
 * Check deposit status enumeration
 * Represents the various states a check deposit can be in during processing
 */
public enum CheckDepositStatus {
    PENDING,                // Initial state, images uploaded
    IMAGE_PROCESSING,       // Processing check images
    MICR_VALIDATION,       // Validating MICR data
    AMOUNT_VERIFICATION,   // Verifying check amount
    FRAUD_CHECK,          // Running fraud detection
    MANUAL_REVIEW,        // Requires manual review
    APPROVED,             // Approved for deposit
    PROCESSING,           // Being processed by bank
    DEPOSITED,           // Successfully deposited
    PARTIAL_HOLD,        // Deposited with partial hold
    FULL_HOLD,          // Deposited with full hold
    REJECTED,           // Rejected (bad image, fraud, etc.)
    RETURNED,          // Returned by bank
    CANCELLED         // Cancelled by user or system
}