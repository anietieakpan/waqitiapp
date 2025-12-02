package com.waqiti.business.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Kafka Consumer Idempotency Service
 *
 * Prevents duplicate processing of Kafka messages using Redis-based deduplication.
 * Essential for financial services where duplicate processing could result in:
 * - Double charging customers
 * - Duplicate expense reimbursements
 * - Incorrect balance updates
 *
 * Uses Redis SET with NX (if not exists) and EX (expiration) for atomic operations.
 *
 * Default TTL: 24 hours (configurable)
 * - Covers typical retry windows
 * - Prevents indefinite memory growth
 * - Balances safety vs. resource usage
 *
 * CRITICAL: Financial service - every message must be processed exactly once
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2025-01-16
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaIdempotencyService {

    private final RedisTemplate<String, String> redisTemplate;
    private final MeterRegistry meterRegistry;

    private static final String KEY_PREFIX = "kafka:idempotency:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    /**
     * Check if a message has already been processed and mark it as processed if not.
     *
     * This is an atomic operation using Redis SET NX EX command.
     *
     * @param messageKey Unique identifier for the message (topic + partition + offset or custom key)
     * @param consumerGroup Consumer group name for isolation
     * @param ttl Time-to-live for the idempotency key
     * @return true if this is the first time seeing this message (should process),
     *         false if already processed (should skip)
     */
    public boolean tryAcquire(String messageKey, String consumerGroup, Duration ttl) {
        if (messageKey == null || messageKey.isBlank()) {
            log.warn("Null or blank messageKey provided, allowing processing");
            return true; // Allow processing if no key provided
        }

        String redisKey = buildRedisKey(messageKey, consumerGroup);

        try {
            // SET NX EX - Atomic operation
            // Returns true if key was set (first time), false if key already exists
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(redisKey, "processed", ttl.toMillis(), TimeUnit.MILLISECONDS);

            if (Boolean.TRUE.equals(acquired)) {
                log.debug("Idempotency key acquired: messageKey={}, consumerGroup={}, ttl={}",
                        messageKey, consumerGroup, ttl);

                meterRegistry.counter("kafka.idempotency.acquired",
                        "consumer_group", consumerGroup).increment();

                return true;
            } else {
                log.warn("Duplicate message detected: messageKey={}, consumerGroup={} - SKIPPING PROCESSING",
                        messageKey, consumerGroup);

                meterRegistry.counter("kafka.idempotency.duplicate",
                        "consumer_group", consumerGroup).increment();

                return false;
            }

        } catch (Exception e) {
            log.error("Error checking idempotency for messageKey={}, consumerGroup={}. " +
                            "ALLOWING PROCESSING to prevent message loss. Error: {}",
                    messageKey, consumerGroup, e.getMessage(), e);

            meterRegistry.counter("kafka.idempotency.error",
                    "consumer_group", consumerGroup).increment();

            // Fail open: allow processing on Redis errors to prevent message loss
            return true;
        }
    }

    /**
     * Check if message has been processed with default TTL (24 hours)
     */
    public boolean tryAcquire(String messageKey, String consumerGroup) {
        return tryAcquire(messageKey, consumerGroup, DEFAULT_TTL);
    }

    /**
     * Build composite idempotency key for a message from topic, partition, and offset
     *
     * @param topic Kafka topic name
     * @param partition Partition number
     * @param offset Message offset
     * @param consumerGroup Consumer group name
     * @return Composite idempotency key
     */
    public String buildMessageKey(String topic, int partition, long offset, String consumerGroup) {
        return String.format("%s:%d:%d", topic, partition, offset);
    }

    /**
     * Check if a composite message (topic+partition+offset) has been processed
     */
    public boolean tryAcquireByOffset(String topic, int partition, long offset, String consumerGroup) {
        String messageKey = buildMessageKey(topic, partition, offset, consumerGroup);
        return tryAcquire(messageKey, consumerGroup);
    }

    /**
     * Check if a composite message has been processed with custom TTL
     */
    public boolean tryAcquireByOffset(String topic, int partition, long offset, String consumerGroup, Duration ttl) {
        String messageKey = buildMessageKey(topic, partition, offset, consumerGroup);
        return tryAcquire(messageKey, consumerGroup, ttl);
    }

    /**
     * Manually mark a message as processed (for special cases)
     *
     * @param messageKey Unique message identifier
     * @param consumerGroup Consumer group name
     * @param ttl Time-to-live
     */
    public void markProcessed(String messageKey, String consumerGroup, Duration ttl) {
        String redisKey = buildRedisKey(messageKey, consumerGroup);

        try {
            redisTemplate.opsForValue().set(redisKey, "processed", ttl.toMillis(), TimeUnit.MILLISECONDS);

            log.debug("Manually marked message as processed: messageKey={}, consumerGroup={}",
                    messageKey, consumerGroup);

            meterRegistry.counter("kafka.idempotency.manual_mark",
                    "consumer_group", consumerGroup).increment();

        } catch (Exception e) {
            log.error("Error marking message as processed: messageKey={}, consumerGroup={}",
                    messageKey, consumerGroup, e);

            meterRegistry.counter("kafka.idempotency.error",
                    "consumer_group", consumerGroup).increment();
        }
    }

    /**
     * Check if a message key exists (has been processed) without acquiring
     *
     * @param messageKey Unique message identifier
     * @param consumerGroup Consumer group name
     * @return true if message has been processed, false otherwise
     */
    public boolean exists(String messageKey, String consumerGroup) {
        String redisKey = buildRedisKey(messageKey, consumerGroup);

        try {
            Boolean exists = redisTemplate.hasKey(redisKey);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.error("Error checking existence for messageKey={}, consumerGroup={}",
                    messageKey, consumerGroup, e);

            meterRegistry.counter("kafka.idempotency.error",
                    "consumer_group", consumerGroup).increment();

            // Assume not exists on error to allow processing
            return false;
        }
    }

    /**
     * Manually release an idempotency lock (for special cases like manual retry)
     *
     * @param messageKey Unique message identifier
     * @param consumerGroup Consumer group name
     */
    public void release(String messageKey, String consumerGroup) {
        String redisKey = buildRedisKey(messageKey, consumerGroup);

        try {
            Boolean deleted = redisTemplate.delete(redisKey);

            if (Boolean.TRUE.equals(deleted)) {
                log.info("Released idempotency lock: messageKey={}, consumerGroup={}",
                        messageKey, consumerGroup);

                meterRegistry.counter("kafka.idempotency.released",
                        "consumer_group", consumerGroup).increment();
            }
        } catch (Exception e) {
            log.error("Error releasing idempotency lock: messageKey={}, consumerGroup={}",
                    messageKey, consumerGroup, e);

            meterRegistry.counter("kafka.idempotency.error",
                    "consumer_group", consumerGroup).increment();
        }
    }

    /**
     * Get time-to-live for an idempotency key
     *
     * @param messageKey Unique message identifier
     * @param consumerGroup Consumer group name
     * @return TTL in seconds, or -1 if key doesn't exist, -2 if key has no expiration
     */
    public Long getTTL(String messageKey, String consumerGroup) {
        String redisKey = buildRedisKey(messageKey, consumerGroup);

        try {
            return redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Error getting TTL for messageKey={}, consumerGroup={}",
                    messageKey, consumerGroup, e);
            return -1L;
        }
    }

    /**
     * Build Redis key with namespace prefix
     */
    private String buildRedisKey(String messageKey, String consumerGroup) {
        return KEY_PREFIX + consumerGroup + ":" + messageKey;
    }

    /**
     * Clear all idempotency keys for a consumer group (USE WITH CAUTION)
     *
     * This is a dangerous operation that should only be used for testing
     * or emergency recovery scenarios.
     *
     * @param consumerGroup Consumer group to clear
     * @return Number of keys deleted
     */
    public long clearConsumerGroup(String consumerGroup) {
        log.warn("DANGEROUS OPERATION: Clearing all idempotency keys for consumer group: {}",
                consumerGroup);

        try {
            String pattern = KEY_PREFIX + consumerGroup + ":*";
            var keys = redisTemplate.keys(pattern);

            if (keys != null && !keys.isEmpty()) {
                Long deleted = redisTemplate.delete(keys);

                log.warn("Deleted {} idempotency keys for consumer group: {}",
                        deleted, consumerGroup);

                meterRegistry.counter("kafka.idempotency.bulk_clear",
                        "consumer_group", consumerGroup).increment();

                return deleted != null ? deleted : 0L;
            }

            return 0L;
        } catch (Exception e) {
            log.error("Error clearing consumer group: {}", consumerGroup, e);

            meterRegistry.counter("kafka.idempotency.error",
                    "consumer_group", consumerGroup).increment();

            return 0L;
        }
    }

    /**
     * Get statistics for a consumer group
     *
     * @param consumerGroup Consumer group name
     * @return Number of active idempotency keys
     */
    public long getActiveKeyCount(String consumerGroup) {
        try {
            String pattern = KEY_PREFIX + consumerGroup + ":*";
            var keys = redisTemplate.keys(pattern);
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.error("Error getting key count for consumer group: {}", consumerGroup, e);
            return 0L;
        }
    }
}
