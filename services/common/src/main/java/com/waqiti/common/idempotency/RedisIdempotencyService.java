package com.waqiti.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Optional;

/**
 * Universal Redis-backed Idempotency Service
 *
 * Provides idempotent processing for financial operations and event consumers.
 * Prevents duplicate processing of payments, transactions, and critical events.
 *
 * Features:
 * - Redis-backed with configurable TTL (default: 24 hours)
 * - Thread-safe atomic operations
 * - Result caching for duplicate requests
 * - Comprehensive metrics
 * - Automatic cleanup via Redis expiration
 *
 * Usage Pattern:
 * <pre>
 * if (idempotencyService.isProcessed(idempotencyKey)) {
 *     return idempotencyService.getProcessedResult(idempotencyKey, ResultType.class)
 *         .orElse(defaultResult);
 * }
 *
 * Result result = processEvent(...);
 * idempotencyService.markProcessed(idempotencyKey, result, Duration.ofHours(24));
 * </pre>
 *
 * Key Format: {service}:{eventType}:{eventId}
 * Example: payment-service:PaymentCompleted:pay_123456789
 *
 * @author Waqiti Platform - Event Architecture Team
 * @version 2.0
 * @since 2025-10-11
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RedisIdempotencyService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private Counter processedCounter;
    private Counter duplicateCounter;
    private Counter storeCounter;
    private Counter retrieveCounter;

    private static final String IDEMPOTENCY_PREFIX = "idempotency:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    private static final Duration FINANCIAL_TTL = Duration.ofDays(30); // Financial operations: 30 days

    @PostConstruct
    public void initMetrics() {
        processedCounter = Counter.builder("idempotency.processed")
            .description("Total idempotency checks performed")
            .register(meterRegistry);

        duplicateCounter = Counter.builder("idempotency.duplicate")
            .description("Total duplicate requests detected")
            .register(meterRegistry);

        storeCounter = Counter.builder("idempotency.stored")
            .description("Total idempotency keys stored")
            .register(meterRegistry);

        retrieveCounter = Counter.builder("idempotency.retrieved")
            .description("Total cached results retrieved")
            .register(meterRegistry);
    }

    /**
     * Check if event/request has already been processed
     *
     * @param idempotencyKey Unique key for the operation
     * @return true if already processed
     */
    public boolean isProcessed(String idempotencyKey) {
        processedCounter.increment();
        String key = buildKey(idempotencyKey);

        Boolean exists = redisTemplate.hasKey(key);
        boolean isProcessed = Boolean.TRUE.equals(exists);

        if (isProcessed) {
            duplicateCounter.increment();
            log.info("⏭️ Idempotency: Duplicate detected | key={}", maskKey(idempotencyKey));
        }

        return isProcessed;
    }

    /**
     * Mark operation as processed (without storing result)
     *
     * @param idempotencyKey Unique key
     * @param ttl Time to live
     */
    public void markProcessed(String idempotencyKey, Duration ttl) {
        String key = buildKey(idempotencyKey);
        redisTemplate.opsForValue().set(key, true, ttl);
        storeCounter.increment();

        log.debug("✅ Idempotency: Marked processed | key={}, ttl={}",
            maskKey(idempotencyKey), ttl);
    }

    /**
     * Mark operation as processed with default TTL (24 hours)
     *
     * @param idempotencyKey Unique key
     */
    public void markProcessed(String idempotencyKey) {
        markProcessed(idempotencyKey, DEFAULT_TTL);
    }

    /**
     * Mark financial operation as processed (30-day TTL)
     *
     * @param idempotencyKey Unique key
     */
    public void markFinancialOperationProcessed(String idempotencyKey) {
        markProcessed(idempotencyKey, FINANCIAL_TTL);
    }

    /**
     * Store operation result for idempotent retrieval
     *
     * @param idempotencyKey Unique key
     * @param result Result object to cache
     * @param ttl Time to live
     */
    public <T> void markProcessed(String idempotencyKey, T result, Duration ttl) {
        String key = buildKey(idempotencyKey);

        try {
            // Store serialized result
            String json = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(key, json, ttl);
            storeCounter.increment();

            log.debug("✅ Idempotency: Stored result | key={}, ttl={}, resultType={}",
                maskKey(idempotencyKey), ttl, result.getClass().getSimpleName());

        } catch (Exception e) {
            log.error("Failed to serialize idempotency result: key={}", maskKey(idempotencyKey), e);
            // Fallback: store boolean flag
            redisTemplate.opsForValue().set(key, true, ttl);
        }
    }

    /**
     * Retrieve cached result from previous processing
     *
     * @param idempotencyKey Unique key
     * @param resultClass Expected result class
     * @return Optional containing cached result if available
     */
    public <T> Optional<T> getProcessedResult(String idempotencyKey, Class<T> resultClass) {
        String key = buildKey(idempotencyKey);

        try {
            Object value = redisTemplate.opsForValue().get(key);

            if (value == null) {
                return Optional.empty();
            }

            // Handle boolean flag (no result stored)
            if (value instanceof Boolean) {
                log.debug("⏭️ Idempotency: Result not cached (boolean flag) | key={}",
                    maskKey(idempotencyKey));
                return Optional.empty();
            }

            // Deserialize result
            if (value instanceof String) {
                T result = objectMapper.readValue((String) value, resultClass);
                retrieveCounter.increment();

                log.info("⏭️ Idempotency: Retrieved cached result | key={}, resultType={}",
                    maskKey(idempotencyKey), resultClass.getSimpleName());

                return Optional.of(result);
            }

            log.warn("⚠️ Idempotency: Unexpected value type | key={}, type={}",
                maskKey(idempotencyKey), value.getClass().getSimpleName());
            return Optional.empty();

        } catch (Exception e) {
            log.error("Failed to deserialize idempotency result: key={}", maskKey(idempotencyKey), e);
            return Optional.empty();
        }
    }

    /**
     * Build idempotency key with standard format
     *
     * @param service Service name
     * @param eventType Event type/class name
     * @param eventId Unique event ID
     * @return Formatted idempotency key
     */
    public String buildIdempotencyKey(String service, String eventType, String eventId) {
        return String.format("%s:%s:%s", service, eventType, eventId);
    }

    /**
     * Build idempotency key for Kafka event
     *
     * @param serviceName Consuming service name
     * @param event Event object (must have getEventId() or getId() method)
     * @return Formatted idempotency key
     */
    public String buildEventIdempotencyKey(String serviceName, Object event) {
        String eventType = event.getClass().getSimpleName();
        String eventId = extractEventId(event);
        return buildIdempotencyKey(serviceName, eventType, eventId);
    }

    /**
     * Remove idempotency record (use with caution!)
     *
     * @param idempotencyKey Key to remove
     */
    public void clearIdempotency(String idempotencyKey) {
        String key = buildKey(idempotencyKey);
        Boolean deleted = redisTemplate.delete(key);

        log.warn("🗑️ Idempotency: Cleared | key={}, existed={}",
            maskKey(idempotencyKey), deleted);
    }

    /**
     * Get remaining TTL for idempotency key
     *
     * @param idempotencyKey Key to check
     * @return Duration remaining, or empty if not found
     */
    public Optional<Duration> getRemainingTTL(String idempotencyKey) {
        String key = buildKey(idempotencyKey);
        Long seconds = redisTemplate.getExpire(key);

        if (seconds == null || seconds < 0) {
            return Optional.empty();
        }

        return Optional.of(Duration.ofSeconds(seconds));
    }

    // Private helper methods

    private String buildKey(String idempotencyKey) {
        return IDEMPOTENCY_PREFIX + idempotencyKey;
    }

    private String extractEventId(Object event) {
        try {
            // Try getEventId() first
            var method = event.getClass().getMethod("getEventId");
            Object result = method.invoke(event);
            return result != null ? result.toString() : "unknown";
        } catch (Exception e1) {
            try {
                // Fallback to getId()
                var method = event.getClass().getMethod("getId");
                Object result = method.invoke(event);
                return result != null ? result.toString() : "unknown";
            } catch (Exception e2) {
                log.warn("Unable to extract event ID from {}", event.getClass().getSimpleName());
                return event.hashCode() + "";
            }
        }
    }

    private String maskKey(String key) {
        if (key == null || key.length() < 8) {
            return "***";
        }
        return key.substring(0, 4) + "***" + key.substring(key.length() - 4);
    }
}
