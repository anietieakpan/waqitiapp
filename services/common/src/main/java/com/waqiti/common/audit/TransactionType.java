package com.waqiti.common.audit;

/**
 * Transaction Type Enumeration for Audit Purposes
 * 
 * Defines the different types of transactions for audit logging.
 * This is a subset of the full transaction types used specifically for audit trails.
 */
public enum TransactionType {
    
    /**
     * Peer-to-peer transfer between users
     */
    P2P_TRANSFER,
    
    /**
     * Bank deposit to wallet
     */
    BANK_DEPOSIT,
    
    /**
     * Bank withdrawal from wallet
     */
    BANK_WITHDRAWAL,
    
    /**
     * Mobile money transaction
     */
    MOBILE_MONEY,
    
    /**
     * Card payment
     */
    CARD_PAYMENT,
    
    /**
     * Merchant payment
     */
    MERCHANT_PAYMENT,
    
    /**
     * Bill payment
     */
    BILL_PAYMENT,
    
    /**
     * Fee charge
     */
    FEE,
    
    /**
     * Refund transaction
     */
    REFUND,
    
    /**
     * Reversal transaction
     */
    REVERSAL,
    
    /**
     * Loan transaction
     */
    LOAN,
    
    /**
     * Other transaction type
     */
    OTHER
}