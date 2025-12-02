/**
 * Address Type Enum
 * Types of cryptocurrency addresses
 */
package com.waqiti.crypto.entity;

public enum AddressType {
    RECEIVING("Receiving address for incoming transactions"),
    CHANGE("Change address for transaction outputs");

    private final String description;

    AddressType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isReceiving() {
        return this == RECEIVING;
    }

    public boolean isChange() {
        return this == CHANGE;
    }
}