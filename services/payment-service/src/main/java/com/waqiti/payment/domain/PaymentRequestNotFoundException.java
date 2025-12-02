package com.waqiti.payment.domain;

import java.util.UUID; /**
 * Thrown when a payment request is not found
 */
public class PaymentRequestNotFoundException extends RuntimeException {
    public PaymentRequestNotFoundException(UUID id) {
        super("Payment request not found with ID: " + id);
    }
}
