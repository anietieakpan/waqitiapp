package com.waqiti.wallet.domain;

/**
 * Represents the possible types of transactions
 */
public enum TransactionType {
    CREDIT, // Money credited to wallet
    DEBIT, // Money debited from wallet
    DEPOSIT, // Money coming into a wallet from an external source
    WITHDRAWAL, // Money going out of a wallet to an external destination
    TRANSFER, // Money moving from one wallet to another
    TRANSFER_IN, // Incoming transfer
    TRANSFER_OUT, // Outgoing transfer
    PAYMENT, // Payment for goods or services
    REFUND, // Refund of a previous payment
    FEE, // Service fee
    INTEREST, // Interest payment
    CASHBACK, // Cashback reward
    ADJUSTMENT // Manual adjustment
}

