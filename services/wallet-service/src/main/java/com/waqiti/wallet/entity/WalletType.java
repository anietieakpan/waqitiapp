package com.waqiti.wallet.entity;

/**
 * Wallet type enumeration for different wallet purposes.
 */
public enum WalletType {
    /**
     * Primary personal wallet for individual users.
     */
    PERSONAL,
    
    /**
     * Business wallet for merchants and companies.
     */
    BUSINESS,
    
    /**
     * Savings wallet with restricted access.
     */
    SAVINGS,
    
    /**
     * Escrow wallet for holding funds temporarily.
     */
    ESCROW,
    
    /**
     * System wallet for internal operations.
     */
    SYSTEM,
    
    /**
     * Multi-signature wallet requiring multiple approvals.
     */
    MULTISIG,
    
    /**
     * Cryptocurrency wallet.
     */
    CRYPTO,
    
    /**
     * Joint wallet shared between multiple users.
     */
    JOINT
}