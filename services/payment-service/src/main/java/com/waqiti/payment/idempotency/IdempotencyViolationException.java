package com.waqiti.payment.idempotency;

/**
 * Exception thrown when idempotency violation is detected
 *
 * SCENARIOS:
 * 1. Concurrent duplicate requests (both in PROCESSING state)
 * 2. Attempt to create duplicate idempotency record
 * 3. Idempotency key reuse across different request types
 */
public class IdempotencyViolationException extends RuntimeException {

    public IdempotencyViolationException(String message) {
        super(message);
    }

    public IdempotencyViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
