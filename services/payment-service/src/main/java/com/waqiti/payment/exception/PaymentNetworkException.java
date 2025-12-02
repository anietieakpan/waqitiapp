package com.waqiti.payment.exception;

import org.springframework.http.HttpStatus;

public class PaymentNetworkException extends PaymentProcessingException {
    public PaymentNetworkException(String message) {
        super(message, "PAYMENT_NETWORK_ERROR", HttpStatus.SERVICE_UNAVAILABLE);
    }
    
    public PaymentNetworkException(String message, Throwable cause) {
        super(message, "PAYMENT_NETWORK_ERROR", cause);
    }
}
