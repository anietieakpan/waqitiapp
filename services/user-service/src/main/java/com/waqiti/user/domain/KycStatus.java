package com.waqiti.user.domain;

/**
 * Represents the possible states of KYC (Know Your Customer) verification
 */
public enum KycStatus {
    NOT_STARTED,      // KYC verification not yet started
    PENDING,          // KYC verification pending (documents uploaded, awaiting verification)
    IN_PROGRESS,      // KYC verification is in progress
    PENDING_REVIEW,   // KYC verification is pending manual review
    REQUIRES_MANUAL_REVIEW, // KYC verification requires manual review
    VERIFIED,         // KYC verification is verified/approved
    APPROVED,         // KYC verification is approved (alias for VERIFIED)
    REJECTED          // KYC verification is rejected
}