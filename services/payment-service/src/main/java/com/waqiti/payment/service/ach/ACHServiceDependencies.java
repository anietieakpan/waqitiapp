package com.waqiti.payment.service.ach;

import com.waqiti.common.kafka.IdempotencyHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * ACH Service Dependencies
 *
 * Facade for ACH-specific service integrations
 * Wraps common services for use in ACHBatchProcessorService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ACHServiceDependencies {

    private final IdempotencyHandler idempotencyHandler;
    private final RedissonClient redissonClient;

    /**
     * Check idempotency for ACH batch operations
     */
    public <T> ACHIdempotencyResult<T> checkIdempotency(String key, Class<T> resultClass) {
        if (idempotencyHandler.isDuplicate(createMockRecord(key))) {
            log.debug("Duplicate ACH operation detected: {}", key);
            // In real implementation, retrieve cached result from Redis
            return ACHIdempotencyResult.duplicate(null, key);
        }
        return ACHIdempotencyResult.newOperation(key);
    }

    private ConsumerRecord<String,?> createMockRecord(String key) {
        return null; //TODO. You must fully implement this code - aniix 28th October, 2025
    }

    /**
     * Store idempotency result
     */
    public <T> void storeIdempotencyResult(String key, T result, Duration ttl, Map<String, String> metadata) {
        // Mark as processed using IdempotencyHandler
        idempotencyHandler.markProcessed(createMockRecord(key), ttl);
        log.debug("Stored idempotency result for key: {}", key);
    }

    /**
     * Acquire distributed lock
     */
    public boolean acquireLock(String lockKey, Duration timeout) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            return lock.tryLock(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("Failed to acquire lock: {}", lockKey, e);
            return false;
        }
    }

    /**
     * Release distributed lock
     */
    public void releaseLock(String lockKey) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Released lock: {}", lockKey);
            }
        } catch (Exception e) {
            log.error("Failed to release lock: {}", lockKey, e);
        }
    }

    /**
     * Create Kafka consumer record for idempotency checking
     *
     * This method creates a properly formatted Kafka ConsumerRecord for ACH operations.
     * The record is used for idempotency validation in the event processing pipeline.
     *
     * @param key The message key (typically ACH transaction ID)
     * @param value The message value (ACH operation data)
     * @param topic The Kafka topic name
     * @param partition The partition number
     * @param offset The message offset
     * @return A properly formatted ConsumerRecord
     */
    public org.apache.kafka.clients.consumer.ConsumerRecord<String, Object> createConsumerRecord(
            String key,
            Object value,
            String topic,
            int partition,
            long offset) {

        // Create properly formatted Kafka record with all required metadata
        return new org.apache.kafka.clients.consumer.ConsumerRecord<>(
            topic != null ? topic : "ach-operations", // Topic name
            partition, // Partition number
            offset, // Message offset
            System.currentTimeMillis(), // Timestamp (when record was created)
            org.apache.kafka.common.record.TimestampType.CREATE_TIME, // Timestamp type
            -1L, // Checksum (deprecated in newer Kafka versions)
            org.apache.kafka.clients.consumer.ConsumerRecord.NULL_SIZE, // Serialized key size
            org.apache.kafka.clients.consumer.ConsumerRecord.NULL_SIZE, // Serialized value size
            key, // Message key
            value, // Message value
            new org.apache.kafka.common.header.internals.RecordHeaders(), // Headers
            java.util.Optional.empty() // Leader epoch
        );
    }

    /**
     * Create consumer record with default topic and partition
     * Used for simple idempotency checks where only key and value are needed
     */
    public org.apache.kafka.clients.consumer.ConsumerRecord<String, Object> createConsumerRecord(
            String key,
            Object value) {

        return createConsumerRecord(key, value, "ach-operations", 0, System.currentTimeMillis());
    }

    /**
     * Create consumer record with auto-generated offset
     * Useful for testing and development scenarios
     */
    public org.apache.kafka.clients.consumer.ConsumerRecord<String, Object> createConsumerRecordWithAutoOffset(
            String key,
            Object value,
            String topic) {

        // Generate offset based on current timestamp to ensure uniqueness
        long offset = System.currentTimeMillis();

        return createConsumerRecord(key, value, topic, 0, offset);
    }

    /**
     * Validate consumer record for ACH operations
     * Ensures the record has all required fields for safe processing
     */
    public boolean validateConsumerRecord(org.apache.kafka.clients.consumer.ConsumerRecord<String, Object> record) {
        if (record == null) {
            log.error("Consumer record is null");
            return false;
        }

        if (record.key() == null) {
            log.error("Consumer record key is null - record will not be idempotent");
            return false;
        }

        if (record.value() == null) {
            log.error("Consumer record value is null - nothing to process");
            return false;
        }

        if (record.topic() == null || record.topic().isEmpty()) {
            log.warn("Consumer record topic is missing - using default");
        }

        return true;
    }
}

