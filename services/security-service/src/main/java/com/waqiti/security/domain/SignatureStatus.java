package com.waqiti.security.domain;

public enum SignatureStatus {
    PENDING_SIGNATURES("Pending Signatures", "Waiting for additional signatures"),
    ACTIVE("Active", "Signature is valid and active"),
    EXPIRED("Expired", "Signature has expired"),
    REVOKED("Revoked", "Signature has been revoked"),
    FAILED("Failed", "Signature verification failed"),
    INVALID("Invalid", "Signature is invalid");

    private final String displayName;
    private final String description;

    SignatureStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isValid() {
        return this == ACTIVE;
    }

    public boolean isPending() {
        return this == PENDING_SIGNATURES;
    }

    public boolean isTerminal() {
        return this == EXPIRED || this == REVOKED || this == FAILED || this == INVALID;
    }
}