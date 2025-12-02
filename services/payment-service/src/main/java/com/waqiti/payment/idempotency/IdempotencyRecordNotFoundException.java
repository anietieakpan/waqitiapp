package com.waqiti.payment.idempotency;

/**
 * Exception thrown when idempotency record not found
 */
public class IdempotencyRecordNotFoundException extends RuntimeException {

    public IdempotencyRecordNotFoundException(String message) {
        super(message);
    }

    public IdempotencyRecordNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
