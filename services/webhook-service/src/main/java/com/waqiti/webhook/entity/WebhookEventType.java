package com.waqiti.webhook.entity;

public enum WebhookEventType {
    // Payment events
    PAYMENT_CREATED("Payment created"),
    PAYMENT_COMPLETED("Payment completed successfully"),
    PAYMENT_FAILED("Payment failed"),
    PAYMENT_REFUNDED("Payment refunded"),
    PAYMENT_CANCELLED("Payment cancelled"),
    
    // Transaction events
    TRANSACTION_CREATED("Transaction created"),
    TRANSACTION_COMPLETED("Transaction completed"),
    TRANSACTION_FAILED("Transaction failed"),
    TRANSACTION_REVERSED("Transaction reversed"),
    
    // Wallet events
    WALLET_CREDITED("Wallet credited"),
    WALLET_DEBITED("Wallet debited"),
    WALLET_LOW_BALANCE("Wallet low balance alert"),
    WALLET_FROZEN("Wallet frozen"),
    WALLET_UNFROZEN("Wallet unfrozen"),
    
    // Dispute events
    DISPUTE_CREATED("Dispute created"),
    DISPUTE_UPDATED("Dispute updated"),
    DISPUTE_RESOLVED("Dispute resolved"),
    DISPUTE_ESCALATED("Dispute escalated"),
    CHARGEBACK_RECEIVED("Chargeback received"),
    
    // Security events
    FRAUD_DETECTED("Fraud detected"),
    SUSPICIOUS_ACTIVITY("Suspicious activity detected"),
    ACCOUNT_LOCKED("Account locked for security"),
    LOGIN_FAILED("Login attempt failed"),
    TWO_FACTOR_REQUIRED("Two-factor authentication required"),
    
    // User events
    USER_REGISTERED("New user registered"),
    USER_VERIFIED("User verification completed"),
    USER_UPDATED("User profile updated"),
    USER_DEACTIVATED("User account deactivated"),
    
    // Compliance events
    KYC_REQUIRED("KYC verification required"),
    KYC_COMPLETED("KYC verification completed"),
    KYC_FAILED("KYC verification failed"),
    AML_ALERT("AML alert triggered"),
    COMPLIANCE_HOLD("Compliance hold placed"),
    
    // System events
    SYSTEM_MAINTENANCE("System maintenance scheduled"),
    SERVICE_DEGRADATION("Service degradation detected"),
    RATE_LIMIT_EXCEEDED("Rate limit exceeded");
    
    private final String description;
    
    WebhookEventType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isCritical() {
        return this == FRAUD_DETECTED || 
               this == CHARGEBACK_RECEIVED || 
               this == AML_ALERT || 
               this == ACCOUNT_LOCKED ||
               this == SERVICE_DEGRADATION;
    }
    
    public boolean isFinancial() {
        return this.name().startsWith("PAYMENT_") || 
               this.name().startsWith("TRANSACTION_") || 
               this.name().startsWith("WALLET_");
    }
}
