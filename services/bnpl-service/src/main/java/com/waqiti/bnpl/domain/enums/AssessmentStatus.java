package com.waqiti.bnpl.domain.enums;

/**
 * Status of a credit assessment
 */
public enum AssessmentStatus {
    PENDING,     // Assessment in progress
    APPROVED,    // Assessment approved
    REJECTED,    // Assessment rejected
    EXPIRED      // Assessment has expired
}