package com.waqiti.wallet.entity;

/**
 * Wallet status enumeration for state management.
 */
public enum WalletStatus {
    /**
     * Wallet is active and can perform all operations.
     */
    ACTIVE,
    
    /**
     * Wallet is temporarily suspended - no debits allowed.
     */
    SUSPENDED,
    
    /**
     * Wallet is frozen - no transactions allowed.
     */
    FROZEN,
    
    /**
     * Wallet is closed - no operations allowed.
     */
    CLOSED,
    
    /**
     * Wallet is pending verification (new accounts).
     */
    PENDING_VERIFICATION,
    
    /**
     * Wallet is under investigation.
     */
    UNDER_INVESTIGATION
}