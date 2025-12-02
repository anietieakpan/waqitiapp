package com.waqiti.payment.commons.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Standardized payment status across all payment services
 */
public enum PaymentStatus {
    
    // Initial states
    PENDING("pending", "Payment is pending processing"),
    INITIATED("initiated", "Payment has been initiated"),
    CREATED("created", "Payment/Transfer has been created"),
    
    // Processing states
    PROCESSING("processing", "Payment is being processed"),
    VALIDATING("validating", "Payment is being validated"),
    AUTHORIZING("authorizing", "Payment is being authorized"),
    FRAUD_CHECKING("fraud_checking", "Payment is undergoing fraud detection"),
    RESERVING_FUNDS("reserving_funds", "Funds are being reserved"),
    FUNDS_RESERVED("funds_reserved", "Funds have been reserved"),
    
    // Intermediate states
    AUTHORIZED("authorized", "Payment has been authorized"),
    CAPTURED("captured", "Payment has been captured"),
    SETTLING("settling", "Payment is settling"),
    
    // Success states
    COMPLETED("completed", "Payment completed successfully"),
    SETTLED("settled", "Payment has been settled"),
    
    // Failure states
    FAILED("failed", "Payment failed"),
    REJECTED("rejected", "Payment was rejected"),
    DECLINED("declined", "Payment was declined"),
    EXPIRED("expired", "Payment has expired"),
    BLOCKED("blocked", "Payment was blocked due to risk/fraud"),
    
    // Cancellation states
    CANCELLED("cancelled", "Payment was cancelled"),
    REFUNDED("refunded", "Payment was refunded"),
    PARTIALLY_REFUNDED("partially_refunded", "Payment was partially refunded"),
    
    // Error states
    ERROR("error", "Payment encountered an error"),
    TIMEOUT("timeout", "Payment timed out"),
    
    // Manual review states
    UNDER_REVIEW("under_review", "Payment is under manual review"),
    FLAGGED("flagged", "Payment has been flagged for review"),
    
    // Special states
    SCHEDULED("scheduled", "Payment is scheduled for future execution"),
    RECURRING("recurring", "Payment is part of a recurring series"),
    ON_HOLD("on_hold", "Payment is on hold");
    
    private final String code;
    private final String description;
    
    PaymentStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    @JsonValue
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    @JsonCreator
    public static PaymentStatus fromCode(String code) {
        for (PaymentStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown payment status code: " + code);
    }
    
    // Status category checks
    public boolean isPending() {
        return this == PENDING || this == INITIATED || this == CREATED || this == SCHEDULED;
    }
    
    public boolean isProcessing() {
        return this == PROCESSING || this == VALIDATING || this == AUTHORIZING || 
               this == AUTHORIZED || this == CAPTURED || this == SETTLING ||
               this == FRAUD_CHECKING || this == RESERVING_FUNDS || this == FUNDS_RESERVED;
    }
    
    public boolean isSuccessful() {
        return this == COMPLETED || this == SETTLED;
    }
    
    public boolean isFailed() {
        return this == FAILED || this == REJECTED || this == DECLINED || 
               this == EXPIRED || this == ERROR || this == TIMEOUT || this == BLOCKED;
    }
    
    public boolean isCancelled() {
        return this == CANCELLED || this == REFUNDED || this == PARTIALLY_REFUNDED;
    }
    
    public boolean isUnderReview() {
        return this == UNDER_REVIEW || this == FLAGGED;
    }
    
    public boolean isFinal() {
        return isSuccessful() || isFailed() || isCancelled();
    }
    
    public boolean canTransitionTo(PaymentStatus newStatus) {
        // Define valid state transitions
        switch (this) {
            case PENDING:
                return newStatus == INITIATED || newStatus == PROCESSING || newStatus == CREATED ||
                       newStatus == CANCELLED || newStatus == FAILED || newStatus == EXPIRED;
                       
            case CREATED:
                return newStatus == FRAUD_CHECKING || newStatus == PROCESSING || 
                       newStatus == CANCELLED || newStatus == FAILED;
                       
            case INITIATED:
                return newStatus == PROCESSING || newStatus == VALIDATING || 
                       newStatus == CANCELLED || newStatus == FAILED;
                       
            case PROCESSING:
                return newStatus == VALIDATING || newStatus == AUTHORIZING || newStatus == FRAUD_CHECKING ||
                       newStatus == COMPLETED || newStatus == FAILED || newStatus == UNDER_REVIEW;
                       
            case FRAUD_CHECKING:
                return newStatus == RESERVING_FUNDS || newStatus == PROCESSING || 
                       newStatus == BLOCKED || newStatus == FAILED || newStatus == UNDER_REVIEW;
                       
            case RESERVING_FUNDS:
                return newStatus == FUNDS_RESERVED || newStatus == FAILED || newStatus == CANCELLED;
                       
            case FUNDS_RESERVED:
                return newStatus == PROCESSING || newStatus == COMPLETED || 
                       newStatus == FAILED || newStatus == CANCELLED;
                       
            case VALIDATING:
                return newStatus == AUTHORIZING || newStatus == REJECTED || 
                       newStatus == UNDER_REVIEW || newStatus == FAILED;
                       
            case AUTHORIZING:
                return newStatus == AUTHORIZED || newStatus == DECLINED || 
                       newStatus == FAILED || newStatus == UNDER_REVIEW;
                       
            case AUTHORIZED:
                return newStatus == CAPTURED || newStatus == CANCELLED || 
                       newStatus == EXPIRED || newStatus == FAILED;
                       
            case CAPTURED:
                return newStatus == SETTLING || newStatus == COMPLETED || 
                       newStatus == REFUNDED || newStatus == FAILED;
                       
            case SETTLING:
                return newStatus == SETTLED || newStatus == FAILED;
                       
            case COMPLETED:
                return newStatus == REFUNDED || newStatus == PARTIALLY_REFUNDED;
                       
            case SETTLED:
                return newStatus == REFUNDED || newStatus == PARTIALLY_REFUNDED;
                
            case UNDER_REVIEW:
                return newStatus == PROCESSING || newStatus == REJECTED || 
                       newStatus == CANCELLED || newStatus == FLAGGED;
                       
            case FLAGGED:
                return newStatus == UNDER_REVIEW || newStatus == REJECTED || 
                       newStatus == CANCELLED;
                       
            case SCHEDULED:
                return newStatus == PENDING || newStatus == CANCELLED || 
                       newStatus == EXPIRED;
                       
            case ON_HOLD:
                return newStatus == PROCESSING || newStatus == CANCELLED || 
                       newStatus == EXPIRED;
                       
            // Final states generally cannot transition
            case FAILED:
            case REJECTED:
            case DECLINED:
            case EXPIRED:
            case BLOCKED:
            case CANCELLED:
            case REFUNDED:
            case PARTIALLY_REFUNDED:
            case ERROR:
            case TIMEOUT:
                return false;
                
            default:
                return false;
        }
    }
    
    @Override
    public String toString() {
        return code;
    }
}