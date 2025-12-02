package com.waqiti.saga.domain;

/**
 * Saga Type Enumeration
 * 
 * Defines the different types of distributed transactions
 * that can be orchestrated by the saga pattern.
 */
public enum SagaType {
    
    /**
     * Peer-to-peer money transfer between users
     * Steps: Validate -> Debit Source -> Credit Destination -> Notify -> Analytics
     */
    P2P_TRANSFER,
    
    /**
     * Bank deposit to user wallet
     * Steps: Validate Bank -> Process Deposit -> Credit Wallet -> Update Balance -> Notify
     */
    BANK_DEPOSIT,
    
    /**
     * Bank withdrawal from user wallet
     * Steps: Validate Wallet -> Debit Wallet -> Process Withdrawal -> Update Balance -> Notify
     */
    BANK_WITHDRAWAL,
    
    /**
     * Payment request processing
     * Steps: Create Request -> Authorize -> Process Payment -> Settle -> Notify
     */
    PAYMENT_REQUEST,
    
    /**
     * Refund processing
     * Steps: Validate Refund -> Process Refund -> Credit Original Payment -> Update Records -> Notify
     */
    REFUND_PROCESSING,
    
    /**
     * User onboarding and account setup
     * Steps: Create Account -> Verify Identity -> Setup Wallet -> Send Welcome -> Analytics
     */
    USER_ONBOARDING,
    
    /**
     * Merchant payment processing
     * Steps: Validate Merchant -> Process Payment -> Credit Merchant -> Fee Processing -> Settlement
     */
    MERCHANT_PAYMENT,
    
    /**
     * Bill payment processing
     * Steps: Validate Bill -> Debit User -> Process Payment -> Update Biller -> Send Receipt
     */
    BILL_PAYMENT,
    
    /**
     * Card payment processing
     * Steps: Validate Card -> Authorize -> Capture -> Settle -> Update Records
     */
    CARD_PAYMENT,
    
    /**
     * Scheduled payment execution
     * Steps: Validate Schedule -> Check Balance -> Process Payment -> Update Schedule -> Notify
     */
    SCHEDULED_PAYMENT,
    
    /**
     * Split payment processing
     * Steps: Validate Splits -> Process Individual Payments -> Aggregate Results -> Notify All
     */
    SPLIT_PAYMENT,
    
    /**
     * Account closure and cleanup
     * Steps: Validate Closure -> Transfer Remaining Balance -> Close Accounts -> Cleanup Data -> Notify
     */
    ACCOUNT_CLOSURE,
    
    /**
     * Fraud investigation and response
     * Steps: Detect Fraud -> Freeze Account -> Investigate -> Take Action -> Notify User
     */
    FRAUD_RESPONSE,
    
    /**
     * Compliance reporting and filing
     * Steps: Collect Data -> Generate Report -> Validate -> Submit -> Archive
     */
    COMPLIANCE_REPORTING,
    
    /**
     * Currency exchange processing
     * Steps: Validate Exchange -> Lock Rate -> Process Exchange -> Update Balances -> Notify
     */
    CURRENCY_EXCHANGE
}