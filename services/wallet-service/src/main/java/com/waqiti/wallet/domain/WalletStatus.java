package com.waqiti.wallet.domain;

/**
 * Represents the possible states of a wallet
 */
public enum WalletStatus {
    ACTIVE, // Wallet is active and can be used for transactions
    FROZEN, // Wallet is temporarily suspended
    SUSPENDED, // Wallet is suspended for security review
    CLOSED // Wallet is permanently closed
}