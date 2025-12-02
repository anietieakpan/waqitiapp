package com.waqiti.lending.service;

import com.waqiti.lending.domain.ProcessedEvent;
import com.waqiti.lending.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4f;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Idempotency Service
 * Ensures events are processed exactly once
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final ProcessedEventRepository processedEventRepository;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String REDIS_KEY_PREFIX = "lending:idempotency:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    /**
     * Check if event has been processed (database)
     */
    @Transactional(readOnly = true)
    public boolean isEventProcessed(String eventId) {
        return processedEventRepository.existsByEventId(eventId);
    }

    /**
     * Mark event as processed (database)
     */
    @Transactional
    public void markEventAsProcessed(String eventId, String eventType, String correlationId) {
        if (!processedEventRepository.existsByEventId(eventId)) {
            ProcessedEvent event = ProcessedEvent.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .correlationId(correlationId)
                    .processedAt(Instant.now())
                    .processingResult("SUCCESS")
                    .expiresAt(Instant.now().plus(DEFAULT_TTL))
                    .build();

            processedEventRepository.save(event);
            log.debug("Marked event as processed: {}", eventId);
        }
    }

    /**
     * Try to acquire idempotency lock with TTL (Redis-based)
     */
    public boolean tryAcquire(String key, Duration ttl) {
        String redisKey = REDIS_KEY_PREFIX + key;
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "LOCKED", ttl.toMillis(), TimeUnit.MILLISECONDS);

        if (Boolean.TRUE.equals(success)) {
            log.debug("Acquired idempotency lock: {}", key);
            return true;
        } else {
            log.debug("Idempotency lock already exists: {}", key);
            return false;
        }
    }

    /**
     * Release idempotency lock
     */
    public void release(String key) {
        String redisKey = REDIS_KEY_PREFIX + key;
        redisTemplate.delete(redisKey);
        log.debug("Released idempotency lock: {}", key);
    }

    /**
     * Clean up expired events (scheduled task)
     */
    @Transactional
    public int cleanupExpiredEvents() {
        int deleted = processedEventRepository.deleteExpiredEvents(Instant.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired processed events", deleted);
        }
        return deleted;
    }
}
