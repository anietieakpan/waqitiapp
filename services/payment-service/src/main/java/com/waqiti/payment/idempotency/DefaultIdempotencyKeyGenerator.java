package com.waqiti.payment.idempotency;

/**
 * Default implementation of idempotency key generator
 */
public class DefaultIdempotencyKeyGenerator implements IdempotencyKeyGenerator {
    @Override
    public String generate(Object request) {
        return java.util.UUID.randomUUID().toString();
    }
}
