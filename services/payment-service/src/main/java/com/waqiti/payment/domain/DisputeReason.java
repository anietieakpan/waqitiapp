package com.waqiti.payment.domain;

/**
 * Enumeration of payment dispute reasons.
 * Based on common chargeback reason codes and industry standards.
 */
public enum DisputeReason {
    
    // Fraud-related disputes
    FRAUD("Fraudulent transaction"),
    UNAUTHORIZED("Unauthorized use of payment method"),
    CARD_NOT_PRESENT("Card not present during transaction"),
    
    // Product/Service disputes
    PRODUCT_NOT_RECEIVED("Product or service not received"),
    DEFECTIVE_PRODUCT("Product defective or not as described"),
    SERVICE_NOT_PROVIDED("Service not provided as agreed"),
    CANCELLED_SUBSCRIPTION("Subscription cancelled but charged"),
    
    // Processing disputes
    DUPLICATE_CHARGE("Duplicate or multiple charges"),
    INCORRECT_AMOUNT("Incorrect charge amount"),
    PROCESSING_ERROR("Payment processing error"),
    CURRENCY_CONVERSION_ERROR("Currency conversion error"),
    
    // Authorization disputes
    AUTHORIZATION_REQUIRED("Authorization required but not obtained"),
    EXPIRED_AUTHORIZATION("Authorization expired"),
    INSUFFICIENT_FUNDS("Insufficient funds at time of authorization"),
    
    // Merchant disputes
    REFUND_NOT_PROCESSED("Refund requested but not processed"),
    RETURN_NOT_ACCEPTED("Product return not accepted by merchant"),
    BILLING_DISPUTE("Billing or subscription dispute"),
    
    // Technical disputes
    SYSTEM_ERROR("System or technical error"),
    NETWORK_ERROR("Network or communication error"),
    
    // Other
    OTHER("Other dispute reason"),
    REGULATORY_REQUIREMENT("Required by regulatory authority");

    private final String description;

    DisputeReason(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isFraudRelated() {
        return this == FRAUD || this == UNAUTHORIZED || this == CARD_NOT_PRESENT;
    }

    public boolean isProductServiceRelated() {
        return this == PRODUCT_NOT_RECEIVED || this == DEFECTIVE_PRODUCT || 
               this == SERVICE_NOT_PROVIDED || this == CANCELLED_SUBSCRIPTION;
    }

    public boolean isProcessingRelated() {
        return this == DUPLICATE_CHARGE || this == INCORRECT_AMOUNT || 
               this == PROCESSING_ERROR || this == CURRENCY_CONVERSION_ERROR;
    }

    public boolean requiresUrgentReview() {
        return isFraudRelated() || this == REGULATORY_REQUIREMENT;
    }

    public int getDefaultResolutionDays() {
        return switch (this) {
            case FRAUD, UNAUTHORIZED, REGULATORY_REQUIREMENT -> 7;
            case DUPLICATE_CHARGE, PROCESSING_ERROR -> 5;
            case PRODUCT_NOT_RECEIVED, SERVICE_NOT_PROVIDED -> 14;
            default -> 10;
        };
    }
}