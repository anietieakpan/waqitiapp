package com.waqiti.dispute.entity;

/**
 * Verification status enumeration
 */
public enum VerificationStatus {
    PENDING("Pending verification"),
    VERIFIED("Evidence verified"),
    REJECTED("Evidence rejected"),
    INSUFFICIENT("Insufficient evidence"),
    FRAUDULENT("Evidence appears fraudulent");

    private final String description;

    VerificationStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
