package com.waqiti.billpayment.exception;

import java.util.UUID;

/**
 * Exception thrown when a payment is not found
 * Results in HTTP 404 Not Found
 */
public class PaymentNotFoundException extends BillPaymentException {

    public PaymentNotFoundException(UUID paymentId) {
        super("PAYMENT_NOT_FOUND", "Payment not found with ID: " + paymentId, paymentId);
    }

    public PaymentNotFoundException(UUID paymentId, String userId) {
        super("PAYMENT_NOT_FOUND",
              String.format("Payment not found with ID: %s for user: %s", paymentId, userId),
              paymentId, userId);
    }

    public PaymentNotFoundException(String message) {
        super("PAYMENT_NOT_FOUND", message);
    }
}
