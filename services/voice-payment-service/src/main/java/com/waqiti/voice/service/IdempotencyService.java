package com.waqiti.voice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Idempotency Service - Prevents duplicate payment processing
 *
 * CRITICAL FOR FINANCIAL SAFETY:
 * - Prevents duplicate payments from retry attempts
 * - Ensures exactly-once processing semantics
 * - Uses Redis for distributed idempotency tracking
 * - TTL-based cleanup of old idempotency keys
 *
 * Pattern:
 * 1. Check if idempotency key exists
 * 2. If exists, return cached response
 * 3. If not, mark as processing and execute
 * 4. Store result for future requests
 *
 * Use Cases:
 * - Network retry scenarios
 * - User clicking submit multiple times
 * - Voice command repeated due to unclear response
 * - System failures during processing
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";
    private static final String PROCESSING_SUFFIX = ":processing";
    private static final String RESULT_SUFFIX = ":result";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    private static final Duration PROCESSING_TTL = Duration.ofMinutes(10);

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Check if idempotency key exists (request already processed)
     *
     * @param idempotencyKey Unique key for this operation
     * @return true if already processed, false otherwise
     */
    public boolean isAlreadyProcessed(String idempotencyKey) {
        String key = buildKey(idempotencyKey);
        Boolean exists = redisTemplate.hasKey(key + RESULT_SUFFIX);

        if (Boolean.TRUE.equals(exists)) {
            log.info("Idempotency key already processed: {}", idempotencyKey);
            return true;
        }

        return false;
    }

    /**
     * Check if request is currently being processed
     *
     * @param idempotencyKey Unique key
     * @return true if currently processing, false otherwise
     */
    public boolean isCurrentlyProcessing(String idempotencyKey) {
        String key = buildKey(idempotencyKey) + PROCESSING_SUFFIX;
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * Mark request as processing (atomic operation)
     * Returns true if successfully marked, false if already processing
     *
     * @param idempotencyKey Unique key
     * @return true if marked successfully, false if already processing
     */
    public boolean markAsProcessing(String idempotencyKey) {
        String key = buildKey(idempotencyKey) + PROCESSING_SUFFIX;

        // Set if absent (atomic operation)
        Boolean set = redisTemplate.opsForValue().setIfAbsent(
                key,
                System.currentTimeMillis(),
                PROCESSING_TTL.toMillis(),
                TimeUnit.MILLISECONDS
        );

        if (Boolean.TRUE.equals(set)) {
            log.info("Marked idempotency key as processing: {}", idempotencyKey);
            return true;
        } else {
            log.warn("Idempotency key already processing: {}", idempotencyKey);
            return false;
        }
    }

    /**
     * Store result for idempotency key
     *
     * @param idempotencyKey Unique key
     * @param result Result to cache
     * @param ttl Time to live
     */
    public void storeResult(String idempotencyKey, Object result, Duration ttl) {
        String resultKey = buildKey(idempotencyKey) + RESULT_SUFFIX;
        String processingKey = buildKey(idempotencyKey) + PROCESSING_SUFFIX;

        // Store result
        redisTemplate.opsForValue().set(resultKey, result, ttl.toMillis(), TimeUnit.MILLISECONDS);

        // Remove processing marker
        redisTemplate.delete(processingKey);

        log.info("Stored idempotent result for key: {} with TTL: {}", idempotencyKey, ttl);
    }

    /**
     * Store result with default TTL (24 hours)
     *
     * @param idempotencyKey Unique key
     * @param result Result to cache
     */
    public void storeResult(String idempotencyKey, Object result) {
        storeResult(idempotencyKey, result, DEFAULT_TTL);
    }

    /**
     * Retrieve cached result for idempotency key
     *
     * @param idempotencyKey Unique key
     * @param resultType Expected result type
     * @return Optional containing result if found
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getResult(String idempotencyKey, Class<T> resultType) {
        String key = buildKey(idempotencyKey) + RESULT_SUFFIX;

        ValueOperations<String, Object> ops = redisTemplate.opsForValue();
        Object result = ops.get(key);

        if (result != null) {
            log.info("Retrieved cached result for idempotency key: {}", idempotencyKey);
            try {
                return Optional.of((T) result);
            } catch (ClassCastException e) {
                log.error("Type mismatch for idempotency key: {}. Expected: {}, Got: {}",
                        idempotencyKey, resultType.getName(), result.getClass().getName());
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    /**
     * Mark processing as failed (remove processing marker)
     *
     * @param idempotencyKey Unique key
     */
    public void markAsFailed(String idempotencyKey) {
        String processingKey = buildKey(idempotencyKey) + PROCESSING_SUFFIX;
        redisTemplate.delete(processingKey);
        log.info("Marked idempotency key as failed: {}", idempotencyKey);
    }

    /**
     * Delete idempotency key and result (for retry scenarios)
     * USE WITH CAUTION: Only for admin operations or error recovery
     *
     * @param idempotencyKey Unique key
     */
    public void invalidate(String idempotencyKey) {
        String baseKey = buildKey(idempotencyKey);
        redisTemplate.delete(baseKey + PROCESSING_SUFFIX);
        redisTemplate.delete(baseKey + RESULT_SUFFIX);
        log.warn("Invalidated idempotency key: {}", idempotencyKey);
    }

    /**
     * Execute operation with idempotency guarantee
     * Template method that handles all idempotency logic
     *
     * @param idempotencyKey Unique key
     * @param operation Operation to execute
     * @param resultType Expected result type
     * @return Operation result (cached if already executed)
     */
    public <T> T executeIdempotent(
            String idempotencyKey,
            IdempotentOperation<T> operation,
            Class<T> resultType) {

        // Check if already processed
        Optional<T> cachedResult = getResult(idempotencyKey, resultType);
        if (cachedResult.isPresent()) {
            log.info("Returning cached result for idempotency key: {}", idempotencyKey);
            return cachedResult.get();
        }

        // Check if currently processing
        if (isCurrentlyProcessing(idempotencyKey)) {
            log.warn("Request already processing for idempotency key: {}", idempotencyKey);
            throw new IdempotencyException(
                    "Request is already being processed. Please wait.",
                    IdempotencyException.ErrorType.ALREADY_PROCESSING
            );
        }

        // Mark as processing
        if (!markAsProcessing(idempotencyKey)) {
            // Race condition - another thread started processing
            throw new IdempotencyException(
                    "Request processing started by another thread",
                    IdempotencyException.ErrorType.CONCURRENT_REQUEST
            );
        }

        try {
            // Execute operation
            log.info("Executing operation for idempotency key: {}", idempotencyKey);
            T result = operation.execute();

            // Store result
            storeResult(idempotencyKey, result);

            return result;

        } catch (Exception e) {
            // Mark as failed (remove processing marker)
            markAsFailed(idempotencyKey);

            log.error("Operation failed for idempotency key: {}", idempotencyKey, e);
            throw e;
        }
    }

    /**
     * Build Redis key with prefix
     */
    private String buildKey(String idempotencyKey) {
        return IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
    }

    /**
     * Functional interface for idempotent operations
     */
    @FunctionalInterface
    public interface IdempotentOperation<T> {
        T execute() throws Exception;
    }

    /**
     * Idempotency exception
     */
    public static class IdempotencyException extends RuntimeException {
        private final ErrorType errorType;

        public enum ErrorType {
            ALREADY_PROCESSING,
            CONCURRENT_REQUEST,
            INVALID_KEY
        }

        public IdempotencyException(String message, ErrorType errorType) {
            super(message);
            this.errorType = errorType;
        }

        public ErrorType getErrorType() {
            return errorType;
        }
    }
}
