package com.waqiti.payment.exception;

// Specific payment exception types
public class CheckProcessingException extends PaymentProcessingException {
    public CheckProcessingException(String message) {
        super(message, "CHECK_PROCESSING_ERROR");
    }
    
    public CheckProcessingException(String message, Throwable cause) {
        super(message, "CHECK_PROCESSING_ERROR", cause);
    }
}
