package com.waqiti.common.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Idempotency Handler for Kafka Message Processing
 *
 * Ensures exactly-once processing semantics by:
 * 1. Tracking processed message IDs in Redis
 * 2. Deduplicating messages based on idempotency key
 * 3. TTL-based cleanup (24-hour retention)
 * 4. Supporting custom idempotency keys per message type
 *
 * Pattern: Idempotent Consumer for event-driven reliability
 * PCI DSS 6.5.3: Protection against replay attacks
 *
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyHandler {

    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    private static final String HEADER_IDEMPOTENCY_KEY = "idempotency-key";
    private static final String HEADER_MESSAGE_ID = "message-id";

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Check if message has already been processed (idempotency check)
     *
     * @param record Kafka record to check
     * @return true if already processed, false if new message
     */
    public boolean isDuplicate(ConsumerRecord<String, ?> record) {
        String idempotencyKey = extractIdempotencyKey(record);
        String redisKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;

        // Check Redis for existing processing record
        Boolean exists = redisTemplate.hasKey(redisKey);
        if (Boolean.TRUE.equals(exists)) {
            log.warn("Duplicate message detected: topic={}, partition={}, offset={}, idempotencyKey={}",
                record.topic(), record.partition(), record.offset(), idempotencyKey);
            return true;
        }

        return false;
    }

    /**
     * Mark message as processed (store idempotency key)
     *
     * @param record Kafka record that was successfully processed
     */
    public void markProcessed(ConsumerRecord<String, ?> record) {
        String idempotencyKey = extractIdempotencyKey(record);
        String redisKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;

        // Store processing record with TTL
        String metadata = buildMetadata(record);
        redisTemplate.opsForValue().set(redisKey, metadata, DEFAULT_TTL);

        log.debug("Marked message as processed: topic={}, partition={}, offset={}, idempotencyKey={}",
            record.topic(), record.partition(), record.offset(), idempotencyKey);
    }

    /**
     * Mark message as processed with custom TTL
     */
    public void markProcessed(ConsumerRecord<String, ?> record, Duration ttl) {
        String idempotencyKey = extractIdempotencyKey(record);
        String redisKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;

        String metadata = buildMetadata(record);
        redisTemplate.opsForValue().set(redisKey, metadata, ttl);

        log.debug("Marked message as processed with custom TTL: topic={}, idempotencyKey={}, ttl={}",
            record.topic(), idempotencyKey, ttl);
    }

    /**
     * Extract idempotency key from record headers or generate one
     *
     * Priority:
     * 1. Custom idempotency-key header (set by producer)
     * 2. Message-id header (UUID from producer)
     * 3. Fallback: topic + partition + offset (Kafka coordinates)
     */
    private String extractIdempotencyKey(ConsumerRecord<String, ?> record) {
        // Try custom idempotency key header
        var idempotencyHeader = record.headers().lastHeader(HEADER_IDEMPOTENCY_KEY);
        if (idempotencyHeader != null) {
            return new String(idempotencyHeader.value());
        }

        // Try message-id header
        var messageIdHeader = record.headers().lastHeader(HEADER_MESSAGE_ID);
        if (messageIdHeader != null) {
            return new String(messageIdHeader.value());
        }

        // Fallback: Use Kafka coordinates (topic + partition + offset)
        // Note: This is safe but not ideal for retries (offset changes)
        return String.format("%s-%d-%d", record.topic(), record.partition(), record.offset());
    }

    /**
     * Build metadata string for Redis storage
     */
    private String buildMetadata(ConsumerRecord<String, ?> record) {
        return String.format("topic=%s,partition=%d,offset=%d,timestamp=%d",
            record.topic(), record.partition(), record.offset(), record.timestamp());
    }

    /**
     * Check and mark pattern (atomic operation)
     *
     * @param record Kafka record
     * @return true if this is a new message (should process), false if duplicate
     */
    public boolean checkAndMark(ConsumerRecord<String, ?> record) {
        String idempotencyKey = extractIdempotencyKey(record);
        String redisKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;

        // Atomic SET NX (set if not exists)
        String metadata = buildMetadata(record);
        Boolean wasSet = redisTemplate.opsForValue().setIfAbsent(redisKey, metadata, DEFAULT_TTL);

        if (Boolean.TRUE.equals(wasSet)) {
            log.debug("New message, marked as processing: idempotencyKey={}", idempotencyKey);
            return true; // New message, proceed with processing
        } else {
            log.warn("Duplicate message detected and skipped: idempotencyKey={}", idempotencyKey);
            return false; // Duplicate, skip processing
        }
    }

    /**
     * Remove idempotency key (for manual reprocessing scenarios)
     */
    public void removeKey(String idempotencyKey) {
        String redisKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        redisTemplate.delete(redisKey);
        log.info("Removed idempotency key for reprocessing: {}", idempotencyKey);
    }

    /**
     * Generate idempotency key for producer (UUID)
     */
    public static String generateIdempotencyKey() {
        return UUID.randomUUID().toString();
    }
}
