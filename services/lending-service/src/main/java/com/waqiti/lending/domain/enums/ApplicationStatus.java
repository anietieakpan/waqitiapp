package com.waqiti.lending.domain.enums;

/**
 * Loan Application Status
 */
public enum ApplicationStatus {
    SUBMITTED,           // Application submitted by borrower
    PENDING,            // Awaiting initial review
    UNDER_REVIEW,       // Being reviewed by underwriter
    MANUAL_REVIEW,      // Requires manual intervention
    APPROVED,           // Application approved
    CONDITIONALLY_APPROVED, // Approved with conditions
    REJECTED,           // Application rejected
    WITHDRAWN,          // Withdrawn by applicant
    EXPIRED,            // Application offer expired
    FUNDED              // Loan disbursed
}
