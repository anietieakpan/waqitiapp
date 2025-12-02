package com.waqiti.payment.domain;

import java.util.UUID;

/**
 * Thrown when payment limits are exceeded
 */
public class PaymentLimitExceededException extends RuntimeException {
    public PaymentLimitExceededException(String message) {
        super(message);
    }
}