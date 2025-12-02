package com.waqiti.billpayment.exception;

import java.util.UUID;

/**
 * Exception thrown when a duplicate payment is detected
 * Results in HTTP 409 Conflict
 */
public class DuplicatePaymentException extends BillPaymentException {

    public DuplicatePaymentException(String idempotencyKey, UUID existingPaymentId) {
        super("DUPLICATE_PAYMENT",
              String.format("Duplicate payment detected with idempotency key: %s. Existing payment ID: %s",
                          idempotencyKey, existingPaymentId),
              idempotencyKey, existingPaymentId);
    }

    public DuplicatePaymentException(String message) {
        super("DUPLICATE_PAYMENT", message);
    }
}
