package com.waqiti.payment.idempotency;

/**
 * Interface for custom idempotency key generators
 */
public interface IdempotencyKeyGenerator {
    String generate(Object request);
}
