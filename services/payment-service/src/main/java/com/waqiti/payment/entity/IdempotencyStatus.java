package com.waqiti.payment.entity;

/**
 * Status of idempotency record
 *
 * PROCESSING: Request is currently being processed
 * COMPLETED: Request completed successfully
 * FAILED: Request failed and can be retried
 */
public enum IdempotencyStatus {
    PROCESSING,
    COMPLETED,
    FAILED
}
