package com.waqiti.common.idempotency;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Enterprise-grade distributed idempotency service using Redis.
 * Ensures exactly-once processing across multiple service instances.
 *
 * Features:
 * - Distributed state management via Redis
 * - TTL-based automatic cleanup
 * - Metrics and monitoring
 * - High availability support
 * - Optimized for low latency
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedIdempotencyService {

    private final RedisTemplate<String, IdempotencyRecord> redisTemplate;
    private final MeterRegistry meterRegistry;

    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    // Metrics
    private final Counter cacheHits;
    private final Counter cacheMisses;
    private final Counter cacheErrors;
    private final Timer checkLatency;

    public DistributedIdempotencyService(RedisTemplate<String, IdempotencyRecord> redisTemplate,
                                        MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;

        // Initialize metrics
        this.cacheHits = Counter.builder("idempotency_cache_hits_total")
                .description("Total idempotency cache hits")
                .register(meterRegistry);
        this.cacheMisses = Counter.builder("idempotency_cache_misses_total")
                .description("Total idempotency cache misses")
                .register(meterRegistry);
        this.cacheErrors = Counter.builder("idempotency_cache_errors_total")
                .description("Total idempotency cache errors")
                .register(meterRegistry);
        this.checkLatency = Timer.builder("idempotency_check_duration")
                .description("Time taken to check idempotency")
                .register(meterRegistry);
    }

    /**
     * Check if an event has already been processed.
     * Thread-safe and distributed across all instances.
     *
     * @param eventId Unique event identifier
     * @return true if already processed, false otherwise
     */
    public boolean isProcessed(String eventId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String key = buildKey(eventId);

        try {
            Boolean exists = redisTemplate.hasKey(key);

            if (Boolean.TRUE.equals(exists)) {
                cacheHits.increment();
                log.debug("Idempotency check - event already processed: {}", eventId);
                sample.stop(checkLatency);
                return true;
            } else {
                cacheMisses.increment();
                sample.stop(checkLatency);
                return false;
            }

        } catch (Exception e) {
            cacheErrors.increment();
            log.error("Error checking idempotency for event: {}", eventId, e);
            sample.stop(checkLatency);

            // Fail open - allow processing on Redis failure
            // This prevents Redis outages from blocking all processing
            // Trade-off: potential duplicate processing vs availability
            return false;
        }
    }

    /**
     * Mark an event as processed with default TTL.
     *
     * @param eventId Unique event identifier
     */
    public void markAsProcessed(String eventId) {
        markAsProcessed(eventId, DEFAULT_TTL);
    }

    /**
     * Mark an event as processed with custom TTL.
     *
     * @param eventId Unique event identifier
     * @param ttl Time-to-live for the idempotency record
     */
    public void markAsProcessed(String eventId, Duration ttl) {
        String key = buildKey(eventId);

        try {
            IdempotencyRecord record = IdempotencyRecord.builder()
                    .eventId(eventId)
                    .processedAt(Instant.now())
                    .ttl(ttl)
                    .build();

            redisTemplate.opsForValue().set(key, record, ttl.toMillis(), TimeUnit.MILLISECONDS);

            log.debug("Marked event as processed: eventId={}, ttl={}s", eventId, ttl.getSeconds());

        } catch (Exception e) {
            cacheErrors.increment();
            log.error("Error marking event as processed: {}", eventId, e);
            // Don't throw - processing should continue even if Redis write fails
        }
    }

    /**
     * Check and mark atomically (if not already processed).
     * Uses Redis SET NX (set if not exists) for atomic operation.
     *
     * @param eventId Unique event identifier
     * @return true if this is the first processing attempt, false if already processed
     */
    public boolean checkAndMark(String eventId) {
        return checkAndMark(eventId, DEFAULT_TTL);
    }

    /**
     * Check and mark atomically with custom TTL.
     *
     * @param eventId Unique event identifier
     * @param ttl Time-to-live
     * @return true if this is the first processing attempt, false if already processed
     */
    public boolean checkAndMark(String eventId, Duration ttl) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String key = buildKey(eventId);

        try {
            IdempotencyRecord record = IdempotencyRecord.builder()
                    .eventId(eventId)
                    .processedAt(Instant.now())
                    .ttl(ttl)
                    .build();

            // Atomic SET NX operation
            Boolean wasAbsent = redisTemplate.opsForValue()
                    .setIfAbsent(key, record, ttl.toMillis(), TimeUnit.MILLISECONDS);

            sample.stop(checkLatency);

            if (Boolean.TRUE.equals(wasAbsent)) {
                cacheMisses.increment();
                log.debug("Event marked for first-time processing: {}", eventId);
                return true;
            } else {
                cacheHits.increment();
                log.debug("Event already processed (duplicate): {}", eventId);
                return false;
            }

        } catch (Exception e) {
            cacheErrors.increment();
            log.error("Error in atomic check-and-mark for event: {}", eventId, e);
            sample.stop(checkLatency);

            // Fail open - allow processing
            return true;
        }
    }

    /**
     * Remove idempotency record (for testing/admin purposes).
     *
     * @param eventId Event identifier
     */
    public void remove(String eventId) {
        String key = buildKey(eventId);

        try {
            redisTemplate.delete(key);
            log.debug("Removed idempotency record: {}", eventId);
        } catch (Exception e) {
            log.error("Error removing idempotency record: {}", eventId, e);
        }
    }

    /**
     * Get idempotency record details (for debugging).
     *
     * @param eventId Event identifier
     * @return Idempotency record or null if not found
     */
    public IdempotencyRecord getRecord(String eventId) {
        String key = buildKey(eventId);

        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("Error getting idempotency record: {}", eventId, e);
            return null;
        }
    }

    private String buildKey(String eventId) {
        return IDEMPOTENCY_KEY_PREFIX + eventId;
    }
}
