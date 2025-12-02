package com.waqiti.payment.exception;

import org.springframework.http.HttpStatus;

public class PaymentSecurityException extends PaymentProcessingException {
    public PaymentSecurityException(String message) {
        super(message, "PAYMENT_SECURITY_ERROR", HttpStatus.FORBIDDEN);
    }
    
    public PaymentSecurityException(String message, Throwable cause) {
        super(message, "PAYMENT_SECURITY_ERROR", cause);
    }
}
