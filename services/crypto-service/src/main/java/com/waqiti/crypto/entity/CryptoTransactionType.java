/**
 * Crypto Transaction Type Enum
 * Types of cryptocurrency transactions
 */
package com.waqiti.crypto.entity;

public enum CryptoTransactionType {
    BUY("Buy cryptocurrency with fiat"),
    SELL("Sell cryptocurrency for fiat"),
    SEND("Send cryptocurrency to external address"),
    RECEIVE("Receive cryptocurrency from external address"),
    CONVERT("Convert between cryptocurrencies"),
    STAKE("Stake cryptocurrency for rewards"),
    UNSTAKE("Unstake cryptocurrency"),
    REWARD("Staking reward received");

    private final String description;

    CryptoTransactionType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isFiatTransaction() {
        return this == BUY || this == SELL;
    }

    public boolean isCryptoTransfer() {
        return this == SEND || this == RECEIVE;
    }

    public boolean isStakingRelated() {
        return this == STAKE || this == UNSTAKE || this == REWARD;
    }

    public boolean requiresExternalAddress() {
        return this == SEND || this == RECEIVE;
    }
}