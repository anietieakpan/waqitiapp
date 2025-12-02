package com.waqiti.common.kyc.model;

/**
 * Status enumeration for KYC verification processes
 */
public enum VerificationStatus {
    PENDING,            // Verification not started
    IN_PROGRESS,        // Verification in progress
    VERIFIED,           // Successfully verified
    APPROVED,           // Manually approved
    REJECTED,           // Verification rejected
    FAILED,             // Verification failed due to error
    PENDING_REVIEW,     // Requires manual review
    EXPIRED,            // Verification expired
    CANCELLED,          // Verification cancelled
    INCOMPLETE          // Missing required information
}