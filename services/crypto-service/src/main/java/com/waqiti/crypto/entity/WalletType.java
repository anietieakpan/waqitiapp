/**
 * Wallet Type Enum
 * Different types of cryptocurrency wallets supported
 */
package com.waqiti.crypto.entity;

public enum WalletType {
    HD("Hierarchical Deterministic"),
    MULTISIG_HD("Multi-Signature HD"),
    HARDWARE("Hardware Wallet");

    private final String description;

    WalletType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}