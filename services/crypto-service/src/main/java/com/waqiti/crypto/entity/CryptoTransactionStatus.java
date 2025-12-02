/**
 * Crypto Transaction Status Enum
 * Status states for cryptocurrency transactions
 */
package com.waqiti.crypto.entity;

public enum CryptoTransactionStatus {
    PENDING("Pending processing"),
    PENDING_DELAY("Pending with security delay"),
    PENDING_APPROVAL("Pending manual approval"),
    PENDING_REVIEW("Pending fraud review"),
    BROADCASTED("Broadcasted to network"),
    CONFIRMED("Confirmed on blockchain"),
    COMPLETED("Transaction completed"),
    FAILED("Transaction failed"),
    CANCELLED("Transaction cancelled");

    private final String description;

    CryptoTransactionStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isPending() {
        return this == PENDING || this == PENDING_DELAY || 
               this == PENDING_APPROVAL || this == PENDING_REVIEW;
    }

    public boolean isInProgress() {
        return this == BROADCASTED || this == CONFIRMED;
    }

    public boolean isFinalized() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    public boolean isSuccessful() {
        return this == COMPLETED;
    }

    public boolean requiresManualIntervention() {
        return this == PENDING_APPROVAL || this == PENDING_REVIEW;
    }
}