package com.waqiti.payment.exception;

public class PaymentMethodLimitExceededException extends RuntimeException {
    public PaymentMethodLimitExceededException(String message) {
        super(message);
    }
}