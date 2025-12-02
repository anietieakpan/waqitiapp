package com.waqiti.payment.exception;

/**
 * Exception thrown when customer verification fails
 */
public class CustomerVerificationException extends RuntimeException {
    
    private final String customerId;
    private final String verificationType;
    private final String failureReason;
    
    public CustomerVerificationException(String message) {
        super(message);
        this.customerId = null;
        this.verificationType = null;
        this.failureReason = null;
    }
    
    public CustomerVerificationException(String message, Throwable cause) {
        super(message, cause);
        this.customerId = null;
        this.verificationType = null;
        this.failureReason = null;
    }
    
    public CustomerVerificationException(String message, String customerId) {
        super(message);
        this.customerId = customerId;
        this.verificationType = null;
        this.failureReason = null;
    }
    
    public CustomerVerificationException(String message, String customerId, String verificationType) {
        super(message);
        this.customerId = customerId;
        this.verificationType = verificationType;
        this.failureReason = null;
    }
    
    public CustomerVerificationException(String message, String customerId, String verificationType, String failureReason) {
        super(message);
        this.customerId = customerId;
        this.verificationType = verificationType;
        this.failureReason = failureReason;
    }
    
    public CustomerVerificationException(String message, String customerId, String verificationType, String failureReason, Throwable cause) {
        super(message, cause);
        this.customerId = customerId;
        this.verificationType = verificationType;
        this.failureReason = failureReason;
    }
    
    public String getCustomerId() {
        return customerId;
    }
    
    public String getVerificationType() {
        return verificationType;
    }
    
    public String getFailureReason() {
        return failureReason;
    }
}