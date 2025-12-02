package com.waqiti.dispute.service;

import com.waqiti.dispute.repository.ProcessedEventRepository;
import com.waqiti.dispute.model.ProcessedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Distributed Idempotency Service using Redis and Database
 *
 * Provides exactly-once semantics for event processing across service instances:
 * - Primary storage: Redis (fast, distributed)
 * - Backup storage: PostgreSQL (persistent, recoverable)
 * - TTL: 7 days for both Redis and database
 *
 * Thread-safe and distributed-safe with Redis locks
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DistributedIdempotencyService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ProcessedEventRepository processedEventRepository;

    private static final String IDEMPOTENCY_PREFIX = "idempotency:";
    private static final String LOCK_PREFIX = "idempotency:lock:";
    private static final Duration TTL = Duration.ofDays(7);
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    /**
     * Check if an event has already been processed
     *
     * @param eventKey Unique event identifier
     * @return true if already processed, false otherwise
     */
    public boolean isAlreadyProcessed(String eventKey) {
        String redisKey = IDEMPOTENCY_PREFIX + eventKey;

        // Check Redis first (fast path)
        Boolean exists = redisTemplate.hasKey(redisKey);
        if (Boolean.TRUE.equals(exists)) {
            log.debug("Event already processed (Redis): {}", eventKey);
            return true;
        }

        // Fallback to database (slower but persistent)
        Optional<ProcessedEvent> dbEvent = processedEventRepository.findByEventKey(eventKey);
        if (dbEvent.isPresent()) {
            log.debug("Event already processed (Database): {}", eventKey);
            // Restore to Redis cache
            restoreToRedisCache(eventKey, dbEvent.get());
            return true;
        }

        return false;
    }

    /**
     * Mark an event as processed with distributed locking
     *
     * @param eventKey Unique event identifier
     * @param operationId Operation identifier
     * @param result Processing result
     * @param ttlDays TTL in days
     */
    @Transactional
    public void markAsProcessed(String eventKey, String operationId, Map<String, Object> result, int ttlDays) {
        String redisKey = IDEMPOTENCY_PREFIX + eventKey;
        String lockKey = LOCK_PREFIX + eventKey;

        try {
            // Acquire distributed lock
            Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(
                lockKey,
                "locked",
                LOCK_TTL
            );

            if (Boolean.FALSE.equals(lockAcquired)) {
                log.warn("Could not acquire lock for event: {}", eventKey);
                // Event might be processed by another instance
                return;
            }

            // Store in Redis
            redisTemplate.opsForHash().putAll(redisKey, Map.of(
                "eventKey", eventKey,
                "operationId", operationId,
                "processedAt", LocalDateTime.now().toString(),
                "result", result != null ? result.toString() : "success"
            ));
            redisTemplate.expire(redisKey, Duration.ofDays(ttlDays));

            // Store in database for persistence
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                .id(UUID.randomUUID().toString())
                .eventKey(eventKey)
                .operationId(operationId)
                .processedAt(LocalDateTime.now())
                .result(result != null ? result.toString() : "success")
                .expiresAt(LocalDateTime.now().plusDays(ttlDays))
                .build();

            processedEventRepository.save(processedEvent);

            log.info("Event marked as processed: {}, operationId: {}", eventKey, operationId);

        } finally {
            // Release lock
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * Get the result of a previously processed event
     *
     * @param eventKey Unique event identifier
     * @return Processing result if found
     */
    public Optional<Map<Object, Object>> getProcessedResult(String eventKey) {
        String redisKey = IDEMPOTENCY_PREFIX + eventKey;

        // Try Redis first
        Map<Object, Object> result = redisTemplate.opsForHash().entries(redisKey);
        if (result != null && !result.isEmpty()) {
            return Optional.of(result);
        }

        // Fallback to database
        return processedEventRepository.findByEventKey(eventKey)
            .map(event -> Map.of(
                "eventKey", (Object) event.getEventKey(),
                "operationId", (Object) event.getOperationId(),
                "processedAt", (Object) event.getProcessedAt().toString(),
                "result", (Object) event.getResult()
            ));
    }

    /**
     * Mark an operation as failed
     *
     * @param eventKey Unique event identifier
     * @param operationId Operation identifier
     * @param errorMessage Error message
     */
    @Transactional
    public void markAsFailed(String eventKey, String operationId, String errorMessage) {
        String redisKey = IDEMPOTENCY_PREFIX + "failed:" + eventKey;

        // Store failure in Redis
        redisTemplate.opsForHash().putAll(redisKey, Map.of(
            "eventKey", eventKey,
            "operationId", operationId,
            "failedAt", LocalDateTime.now().toString(),
            "errorMessage", errorMessage
        ));
        redisTemplate.expire(redisKey, TTL);

        // Store in database
        ProcessedEvent processedEvent = ProcessedEvent.builder()
            .id(UUID.randomUUID().toString())
            .eventKey("failed:" + eventKey)
            .operationId(operationId)
            .processedAt(LocalDateTime.now())
            .result("FAILED: " + errorMessage)
            .expiresAt(LocalDateTime.now().plusDays(7))
            .build();

        processedEventRepository.save(processedEvent);

        log.error("Event marked as failed: {}, error: {}", eventKey, errorMessage);
    }

    /**
     * Clean up expired idempotency records
     * Should be called by scheduled task
     */
    @Transactional
    public void cleanupExpiredRecords() {
        LocalDateTime cutoffTime = LocalDateTime.now();
        List<ProcessedEvent> expired = processedEventRepository.findExpiredEvents(cutoffTime);

        log.info("Cleaning up {} expired idempotency records", expired.size());

        for (ProcessedEvent event : expired) {
            // Remove from Redis
            String redisKey = IDEMPOTENCY_PREFIX + event.getEventKey();
            redisTemplate.delete(redisKey);

            // Remove from database
            processedEventRepository.delete(event);
        }

        log.info("Cleanup completed. Removed {} expired records", expired.size());
    }

    /**
     * Restore cache entry from database to Redis
     */
    private void restoreToRedisCache(String eventKey, ProcessedEvent event) {
        String redisKey = IDEMPOTENCY_PREFIX + eventKey;

        redisTemplate.opsForHash().putAll(redisKey, Map.of(
            "eventKey", event.getEventKey(),
            "operationId", event.getOperationId(),
            "processedAt", event.getProcessedAt().toString(),
            "result", event.getResult()
        ));

        // Calculate remaining TTL
        Duration remainingTTL = Duration.between(LocalDateTime.now(), event.getExpiresAt());
        if (remainingTTL.isPositive()) {
            redisTemplate.expire(redisKey, remainingTTL);
        }

        log.debug("Restored cache for event: {}", eventKey);
    }
}
