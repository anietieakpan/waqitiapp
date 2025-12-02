package com.waqiti.transaction.domain;

/**
 * Transaction Type Enumeration
 * 
 * Defines the different types of transactions supported by the platform.
 */
public enum TransactionType {
    
    /**
     * Peer-to-peer transfer between users
     */
    P2P_TRANSFER,
    
    /**
     * General transfer between accounts
     */
    TRANSFER,
    
    /**
     * Deposit to account/wallet
     */
    DEPOSIT,
    
    /**
     * Withdrawal from account/wallet
     */
    WITHDRAWAL,
    
    /**
     * General payment
     */
    PAYMENT,
    
    /**
     * Bank deposit to wallet
     */
    BANK_DEPOSIT,
    
    /**
     * Bank withdrawal from wallet
     */
    BANK_WITHDRAWAL,
    
    /**
     * Mobile money deposit
     */
    MOBILE_MONEY_DEPOSIT,
    
    /**
     * Mobile money withdrawal
     */
    MOBILE_MONEY_WITHDRAWAL,
    
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
     * Airtime purchase
     */
    AIRTIME_PURCHASE,
    
    /**
     * Cash in at agent
     */
    CASH_IN,
    
    /**
     * Cash out at agent
     */
    CASH_OUT,
    
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
     * Commission payment
     */
    COMMISSION,
    
    /**
     * Loan disbursement
     */
    LOAN_DISBURSEMENT,
    
    /**
     * Loan repayment
     */
    LOAN_REPAYMENT
}