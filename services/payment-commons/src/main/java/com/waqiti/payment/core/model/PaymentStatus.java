package com.waqiti.payment.core.model;

/**
 * Comprehensive payment status enumeration for all payment types
 * Industrial-grade status tracking for financial transactions
 */
public enum PaymentStatus {
    // Initial States
    CREATED("Created", "Payment request created but not yet initiated"),
    PENDING("Pending", "Payment is being processed"),
    INITIATED("Initiated", "Payment has been initiated with provider"),
    
    // Processing States
    PROCESSING("Processing", "Payment is actively being processed"),
    VALIDATING("Validating", "Payment details are being validated"),
    AUTHORIZING("Authorizing", "Payment authorization in progress"),
    AUTHORIZED("Authorized", "Payment has been authorized"),
    CAPTURED("Captured", "Payment has been captured"),
    SETTLING("Settling", "Payment is being settled"),
    
    // Success States
    SUCCESS("Success", "Payment succeeded"),
    COMPLETED("Completed", "Payment successfully completed"),
    SETTLED("Settled", "Payment has been settled"),
    CONFIRMED("Confirmed", "Payment has been confirmed by all parties"),
    PARTIALLY_COMPLETED("Partially Completed", "Payment partially completed"),
    
    // Failure States
    FAILED("Failed", "Payment failed"),
    REJECTED("Rejected", "Payment was rejected"),
    DECLINED("Declined", "Payment was declined by provider"),
    EXPIRED("Expired", "Payment request has expired"),
    CANCELLED("Cancelled", "Payment was cancelled"),
    
    // Special States
    REVERSED("Reversed", "Payment has been reversed"),
    REFUNDED("Refunded", "Payment has been refunded"),
    PARTIALLY_REFUNDED("Partially Refunded", "Payment has been partially refunded"),
    DISPUTED("Disputed", "Payment is under dispute"),
    CHARGEBACK("Chargeback", "Payment has a chargeback"),
    
    // Hold States
    ON_HOLD("On Hold", "Payment is on hold"),
    FRAUD_REVIEW("Fraud Review", "Payment is under fraud review"),
    FRAUD_BLOCKED("Fraud Blocked", "Payment blocked due to fraud detection"),
    COMPLIANCE_REVIEW("Compliance Review", "Payment is under compliance review"),
    MANUAL_REVIEW("Manual Review", "Payment requires manual review"),
    
    // Network-Specific States
    NETWORK_PENDING("Network Pending", "Awaiting network confirmation"),
    NETWORK_CONFIRMED("Network Confirmed", "Confirmed by payment network"),
    NETWORK_FAILED("Network Failed", "Failed at network level"),
    
    // Retry States
    RETRY_PENDING("Retry Pending", "Payment will be retried"),
    RETRYING("Retrying", "Payment retry in progress"),
    MAX_RETRIES_EXCEEDED("Max Retries Exceeded", "Maximum retry attempts exceeded");
    
    private final String displayName;
    private final String description;
    
    PaymentStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isTerminal() {
        return this == COMPLETED || this == SETTLED || this == FAILED || 
               this == REJECTED || this == DECLINED || this == CANCELLED ||
               this == EXPIRED || this == MAX_RETRIES_EXCEEDED;
    }
    
    public boolean isSuccessful() {
        return this == COMPLETED || this == SETTLED || this == CONFIRMED ||
               this == NETWORK_CONFIRMED;
    }
    
    public boolean isPending() {
        return this == PENDING || this == PROCESSING || this == VALIDATING ||
               this == AUTHORIZING || this == SETTLING || this == NETWORK_PENDING ||
               this == RETRY_PENDING || this == RETRYING;
    }
    
    public boolean requiresReview() {
        return this == ON_HOLD || this == FRAUD_REVIEW || 
               this == COMPLIANCE_REVIEW || this == MANUAL_REVIEW;
    }
    
    public boolean isRefundable() {
        return this == COMPLETED || this == SETTLED || this == CONFIRMED;
    }
}