package com.waqiti.payment.domain;

/**
 * Thrown when a payment operation is not allowed due to the status
 */
public class InvalidPaymentStatusException extends RuntimeException {
    public InvalidPaymentStatusException(String message) {
        super(message);
    }
}
