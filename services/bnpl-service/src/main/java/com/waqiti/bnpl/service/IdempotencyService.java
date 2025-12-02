/**
 * Idempotency Service
 * Prevents duplicate processing of events and API requests
 *
 * Critical for financial operations to prevent:
 * - Duplicate charges
 * - Double payments
 * - Repeated event processing
 *
 * Uses Redis for distributed idempotency tracking with TTL
 */
package com.waqiti.bnpl.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Manages idempotency keys for preventing duplicate processing
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String IDEMPOTENCY_KEY_PREFIX = "bnpl:idempotency:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    private static final Duration PAYMENT_TTL = Duration.ofDays(7); // Longer for financial operations

    /**
     * Check if event/request has already been processed
     *
     * @param idempotencyKey Unique identifier for the operation
     * @return true if already processed, false if new
     */
    public boolean isProcessed(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            log.warn("Null or empty idempotency key provided");
            return false;
        }

        String key = buildKey(idempotencyKey);
        Boolean exists = redisTemplate.hasKey(key);

        if (Boolean.TRUE.equals(exists)) {
            log.warn("Duplicate operation detected with idempotency key: {}", idempotencyKey);
            return true;
        }

        return false;
    }

    /**
     * Mark event/request as processed
     *
     * @param idempotencyKey Unique identifier for the operation
     * @param ttl Time-to-live for the idempotency record
     */
    public void markAsProcessed(String idempotencyKey, Duration ttl) {
        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            log.error("Cannot mark null or empty idempotency key as processed");
            return;
        }

        String key = buildKey(idempotencyKey);
        String value = String.valueOf(System.currentTimeMillis());

        redisTemplate.opsForValue().set(key, value, ttl.toMillis(), TimeUnit.MILLISECONDS);
        log.debug("Marked operation as processed: {} (TTL: {})", idempotencyKey, ttl);
    }

    /**
     * Mark event/request as processed with default TTL
     *
     * @param idempotencyKey Unique identifier for the operation
     */
    public void markAsProcessed(String idempotencyKey) {
        markAsProcessed(idempotencyKey, DEFAULT_TTL);
    }

    /**
     * Mark payment operation as processed (longer TTL for financial records)
     *
     * @param idempotencyKey Unique identifier for the payment operation
     */
    public void markPaymentAsProcessed(String idempotencyKey) {
        markAsProcessed(idempotencyKey, PAYMENT_TTL);
    }

    /**
     * Check and mark operation in a single atomic operation
     * Returns true if this is the first time processing
     *
     * @param idempotencyKey Unique identifier for the operation
     * @param ttl Time-to-live for the idempotency record
     * @return true if this is first processing, false if duplicate
     */
    public boolean checkAndMarkProcessed(String idempotencyKey, Duration ttl) {
        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            log.error("Cannot check-and-mark null or empty idempotency key");
            return false;
        }

        String key = buildKey(idempotencyKey);
        String value = String.valueOf(System.currentTimeMillis());

        // Use SET NX (set if not exists) for atomic check-and-set
        Boolean wasSet = redisTemplate.opsForValue().setIfAbsent(key, value, ttl.toMillis(), TimeUnit.MILLISECONDS);

        if (Boolean.TRUE.equals(wasSet)) {
            log.debug("First processing of operation: {}", idempotencyKey);
            return true;
        } else {
            log.warn("Duplicate operation detected and prevented: {}", idempotencyKey);
            return false;
        }
    }

    /**
     * Check and mark operation with default TTL
     *
     * @param idempotencyKey Unique identifier for the operation
     * @return true if this is first processing, false if duplicate
     */
    public boolean checkAndMarkProcessed(String idempotencyKey) {
        return checkAndMarkProcessed(idempotencyKey, DEFAULT_TTL);
    }

    /**
     * Remove idempotency key (for testing or manual intervention)
     *
     * @param idempotencyKey Unique identifier to remove
     */
    public void removeKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            log.warn("Cannot remove null or empty idempotency key");
            return;
        }

        String key = buildKey(idempotencyKey);
        redisTemplate.delete(key);
        log.info("Removed idempotency key: {}", idempotencyKey);
    }

    /**
     * Generate idempotency key for Kafka event
     *
     * @param topic Kafka topic
     * @param partition Partition number
     * @param offset Message offset
     * @return Idempotency key
     */
    public String generateKafkaKey(String topic, int partition, long offset) {
        return String.format("kafka:%s:%d:%d", topic, partition, offset);
    }

    /**
     * Generate idempotency key for payment operation
     *
     * @param userId User identifier
     * @param installmentId Installment identifier
     * @param transactionId Transaction identifier
     * @return Idempotency key
     */
    public String generatePaymentKey(UUID userId, UUID installmentId, String transactionId) {
        return String.format("payment:%s:%s:%s", userId, installmentId, transactionId);
    }

    /**
     * Generate idempotency key for application submission
     *
     * @param userId User identifier
     * @param orderId Order identifier
     * @return Idempotency key
     */
    public String generateApplicationKey(UUID userId, String orderId) {
        return String.format("application:%s:%s", userId, orderId);
    }

    /**
     * Build full Redis key with prefix
     */
    private String buildKey(String idempotencyKey) {
        return IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
    }

    /**
     * Get processing time for idempotency key (when it was first processed)
     *
     * @param idempotencyKey Unique identifier
     * @return Timestamp in milliseconds, or null if not found
     */
    public Long getProcessingTime(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            return null;
        }

        String key = buildKey(idempotencyKey);
        String value = redisTemplate.opsForValue().get(key);

        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                log.error("Invalid processing time value for key: {}", idempotencyKey, e);
                return null;
            }
        }

        return null;
    }

    /**
     * Get remaining TTL for idempotency key
     *
     * @param idempotencyKey Unique identifier
     * @return Remaining TTL in seconds, or null if key doesn't exist
     */
    public Long getRemainingTtl(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            return null;
        }

        String key = buildKey(idempotencyKey);
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);

        return (ttl != null && ttl > 0) ? ttl : null;
    }
}
