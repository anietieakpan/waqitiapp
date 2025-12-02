/**
 * Wallet Status Enum
 * Status states for cryptocurrency wallets
 */
package com.waqiti.crypto.entity;

public enum WalletStatus {
    ACTIVE("Active"),
    INACTIVE("Inactive"),
    FROZEN("Frozen"),
    CLOSED("Closed");

    private final String description;

    WalletStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isOperational() {
        return this == ACTIVE;
    }

    public boolean isRestricted() {
        return this == FROZEN || this == INACTIVE;
    }

    public boolean isPermanentlyClosed() {
        return this == CLOSED;
    }
}