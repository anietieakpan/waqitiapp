package com.waqiti.payment.entity;

/**
 * Check validation status enumeration
 * Represents the overall status of check validation process
 */
public enum CheckValidationStatus {
    PENDING,           // Validation not yet started
    IN_PROGRESS,       // Validation in progress
    PASSED,            // All validations passed
    FAILED,            // Validation failed
    REQUIRES_REVIEW,   // Requires manual review
    TIMEOUT,           // Validation timed out
    ERROR              // Validation error occurred
}