/**
 * Address Status Enum
 * Status states for cryptocurrency addresses
 */
package com.waqiti.crypto.entity;

public enum AddressStatus {
    ACTIVE("Active and available for use"),
    INACTIVE("Inactive"),
    USED("Used for transactions");

    private final String description;

    AddressStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isAvailable() {
        return this == ACTIVE;
    }

    public boolean hasBeenUsed() {
        return this == USED;
    }
}