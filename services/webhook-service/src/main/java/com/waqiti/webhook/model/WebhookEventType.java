package com.waqiti.webhook.model;

/**
 * Enumeration of available webhook event types
 */
public enum WebhookEventType {
    // Payment events
    PAYMENT_CREATED,
    PAYMENT_COMPLETED,
    PAYMENT_FAILED,
    PAYMENT_CANCELLED,
    PAYMENT_REFUNDED,
    
    // User events
    USER_REGISTERED,
    USER_VERIFIED,
    USER_PROFILE_UPDATED,
    USER_DELETED,
    USER_SUSPENDED,
    
    // Transaction events
    TRANSACTION_INITIATED,
    TRANSACTION_COMPLETED,
    TRANSACTION_FAILED,
    TRANSACTION_DISPUTED,
    TRANSACTION_REVERSED,
    
    // Dispute events
    DISPUTE_CREATED,
    DISPUTE_UPDATED,
    DISPUTE_RESOLVED,
    DISPUTE_ESCALATED,
    DISPUTE_CLOSED,
    
    // Security events
    SECURITY_ALERT,
    LOGIN_ATTEMPT,
    PASSWORD_CHANGED,
    ACCOUNT_LOCKED,
    FRAUD_DETECTED,
    
    // Wallet events
    WALLET_CREATED,
    WALLET_BALANCE_UPDATED,
    WALLET_FROZEN,
    WALLET_UNFROZEN,
    
    // KYC events
    KYC_SUBMITTED,
    KYC_APPROVED,
    KYC_REJECTED,
    KYC_EXPIRED,
    
    // System events
    SYSTEM_MAINTENANCE,
    SYSTEM_OUTAGE,
    API_RATE_LIMIT_EXCEEDED,
    
    // Notification events
    NOTIFICATION_SENT,
    NOTIFICATION_FAILED,
    EMAIL_BOUNCED,
    SMS_FAILED
}