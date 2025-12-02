package com.waqiti.wallet.domain;

/**
 * Types of wallets supported by the platform.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
public enum WalletType {
    PERSONAL("Personal Wallet", "Individual user wallet"),
    BUSINESS("Business Wallet", "Corporate/business wallet"),
    MERCHANT("Merchant Wallet", "Merchant payment wallet"),
    ESCROW("Escrow Wallet", "Escrow holding wallet"),
    SAVINGS("Savings Wallet", "Savings account wallet"),
    INVESTMENT("Investment Wallet", "Investment portfolio wallet"),
    CRYPTO("Crypto Wallet", "Cryptocurrency wallet"),
    INTERNAL("Internal Wallet", "Internal system wallet"),
    PREPAID("Prepaid Wallet", "Prepaid card wallet"),
    VIRTUAL("Virtual Wallet", "Virtual card wallet"),
    REWARD("Reward Wallet", "Loyalty rewards wallet");
    
    private final String displayName;
    private final String description;
    
    WalletType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isBusinessWallet() {
        return this == BUSINESS || this == MERCHANT;
    }
    
    public boolean isPersonalWallet() {
        return this == PERSONAL || this == SAVINGS || this == INVESTMENT;
    }
}