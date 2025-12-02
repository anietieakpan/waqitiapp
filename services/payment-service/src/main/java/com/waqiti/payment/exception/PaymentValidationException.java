package com.waqiti.payment.exception;

import org.springframework.http.HttpStatus;

public class PaymentValidationException extends PaymentProcessingException {
    public PaymentValidationException(String message) {
        super(message, "PAYMENT_VALIDATION_ERROR", HttpStatus.BAD_REQUEST);
    }
    
    public PaymentValidationException(String message, Object... args) {
        super(message, "PAYMENT_VALIDATION_ERROR", HttpStatus.BAD_REQUEST, args);
    }
}
