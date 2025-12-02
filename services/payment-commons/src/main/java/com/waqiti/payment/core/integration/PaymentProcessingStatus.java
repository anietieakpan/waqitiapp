package com.waqiti.payment.core.integration;

/**
 * Payment processing status enumeration
 * Comprehensive status tracking for payment processing pipeline
 */
public enum PaymentProcessingStatus {
    // Initial States
    RECEIVED("Payment request received"),
    QUEUED("Payment queued for processing"),
    VALIDATING("Validating payment details"),
    
    // Processing States
    ROUTING("Determining optimal payment route"),
    FRAUD_CHECK("Performing fraud detection"),
    COMPLIANCE_CHECK("Performing compliance checks"),
    PROVIDER_PROCESSING("Processing with payment provider"),
    AUTHORIZING("Authorizing payment"),
    CAPTURING("Capturing payment"),
    
    // Settlement States
    SETTLING("Payment settling"),
    SETTLEMENT_PENDING("Settlement pending"),
    SETTLED("Payment settled"),
    
    // Success States
    COMPLETED("Payment completed successfully"),
    PARTIALLY_COMPLETED("Payment partially completed"),
    
    // Failure States
    VALIDATION_FAILED("Validation failed"),
    FRAUD_BLOCKED("Blocked by fraud detection"),
    COMPLIANCE_BLOCKED("Blocked by compliance"),
    PROVIDER_FAILED("Provider processing failed"),
    AUTHORIZATION_FAILED("Authorization failed"),
    CAPTURE_FAILED("Capture failed"),
    SETTLEMENT_FAILED("Settlement failed"),
    
    // Special States
    CANCELLED("Payment cancelled"),
    EXPIRED("Payment expired"),
    TIMEOUT("Processing timeout"),
    RETRY_PENDING("Retry pending"),
    RETRYING("Retrying payment"),
    MANUAL_REVIEW("Manual review required"),
    ON_HOLD("Payment on hold"),
    
    // Error States
    ERROR("Processing error"),
    SYSTEM_ERROR("System error occurred"),
    NETWORK_ERROR("Network error occurred");
    
    private final String description;
    
    PaymentProcessingStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isTerminal() {
        return this == COMPLETED || 
               this == SETTLED ||
               this == CANCELLED || 
               this == EXPIRED ||
               this == VALIDATION_FAILED ||
               this == FRAUD_BLOCKED ||
               this == COMPLIANCE_BLOCKED;
    }
    
    public boolean isSuccessful() {
        return this == COMPLETED || 
               this == SETTLED ||
               this == PARTIALLY_COMPLETED;
    }
    
    public boolean isProcessing() {
        return !isTerminal() && !isSuccessful();
    }
    
    public boolean requiresAction() {
        return this == MANUAL_REVIEW || 
               this == ON_HOLD ||
               this == RETRY_PENDING;
    }
}