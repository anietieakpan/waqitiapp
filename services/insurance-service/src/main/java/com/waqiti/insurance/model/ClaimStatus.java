package com.waqiti.insurance.model;

/**
 * Insurance Claim Status enum
 */
public enum ClaimStatus {
    SUBMITTED,
    UNDER_REVIEW,
    PENDING_ADJUSTER_REVIEW,
    APPROVED,
    DENIED,
    FRAUDULENT,
    PROCESSING_FAILED,
    REGULATORY_VIOLATION,
    PROCESSED,
    PAID,
    CLOSED
}
