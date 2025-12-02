package com.waqiti.common.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Service for managing idempotency of financial operations.
 * Ensures that financial operations can be safely retried without causing duplicate effects.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    // CRITICAL ENHANCEMENT: Add persistent storage for production reliability
    private final IdempotencyRepository idempotencyRepository;
    private final SecurityContextService securityContextService;

    private static final String IDEMPOTENCY_PREFIX = "idempotency:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    /**
     * Checks if an operation with the given idempotency key has already been processed.
     * 
     * @param idempotencyKey Unique key for the operation
     * @return Optional containing the previous result if found, empty otherwise
     */
    @Timed("idempotency.check")
    public <T> Optional<IdempotencyResult<T>> checkIdempotency(String idempotencyKey, Class<T> resultType) {
        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;
        
        try {
            String value = redisTemplate.opsForValue().get(redisKey);
            
            if (value != null) {
                IdempotencyRecord record = objectMapper.readValue(value, IdempotencyRecord.class);
                
                if (record.getStatus() == IdempotencyStatus.COMPLETED) {
                    T result = objectMapper.readValue(record.getResult(), resultType);
                    log.debug("Idempotency hit for key: {}", idempotencyKey);
                    return Optional.of(new IdempotencyResult<>(result, true));
                } else if (record.getStatus() == IdempotencyStatus.IN_PROGRESS) {
                    log.debug("Operation in progress for key: {}", idempotencyKey);
                    return Optional.of(new IdempotencyResult<>(null, true, true));
                }
            }
            
            return Optional.empty();
            
        } catch (Exception e) {
            log.error("Error checking idempotency for key: {}", idempotencyKey, e);
            return Optional.empty();
        }
    }

    /**
     * Marks an operation as in progress.
     * 
     * @param idempotencyKey Unique key for the operation
     * @param operationId Unique identifier for this operation instance
     * @return true if successfully marked, false if already exists
     */
    @Timed("idempotency.start")
    public boolean startOperation(String idempotencyKey, UUID operationId) {
        return startOperation(idempotencyKey, operationId, DEFAULT_TTL);
    }

    /**
     * Marks an operation as in progress with custom TTL.
     * 
     * @param idempotencyKey Unique key for the operation
     * @param operationId Unique identifier for this operation instance
     * @param ttl Time to live for the idempotency record
     * @return true if successfully marked, false if already exists
     */
    @Timed("idempotency.start")
    public boolean startOperation(String idempotencyKey, UUID operationId, Duration ttl) {
        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;
        
        try {
            IdempotencyRecord record = IdempotencyRecord.builder()
                .idempotencyKey(idempotencyKey)
                .operationId(operationId)
                .status(IdempotencyStatus.IN_PROGRESS)
                .createdAt(Instant.now())
                .build();
            
            String value = objectMapper.writeValueAsString(record);
            Boolean set = redisTemplate.opsForValue().setIfAbsent(redisKey, value, ttl);
            
            if (Boolean.TRUE.equals(set)) {
                log.debug("Operation marked as in progress: {}", idempotencyKey);
                return true;
            } else {
                log.debug("Operation already exists: {}", idempotencyKey);
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error starting operation for key: {}", idempotencyKey, e);
            return false;
        }
    }

    /**
     * Marks an operation as completed and stores the result.
     * 
     * @param idempotencyKey Unique key for the operation
     * @param operationId Unique identifier for this operation instance
     * @param result The result of the operation
     */
    @Timed("idempotency.complete")
    public <T> void completeOperation(String idempotencyKey, UUID operationId, T result) {
        completeOperation(idempotencyKey, operationId, result, DEFAULT_TTL);
    }

    /**
     * Marks an operation as completed and stores the result with custom TTL.
     * 
     * @param idempotencyKey Unique key for the operation
     * @param operationId Unique identifier for this operation instance
     * @param result The result of the operation
     * @param ttl Time to live for the idempotency record
     */
    @Timed("idempotency.complete")
    public <T> void completeOperation(String idempotencyKey, UUID operationId, T result, Duration ttl) {
        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;
        
        try {
            String resultJson = objectMapper.writeValueAsString(result);
            
            IdempotencyRecord record = IdempotencyRecord.builder()
                .idempotencyKey(idempotencyKey)
                .operationId(operationId)
                .status(IdempotencyStatus.COMPLETED)
                .result(resultJson)
                .createdAt(Instant.now())
                .completedAt(Instant.now())
                .build();
            
            String value = objectMapper.writeValueAsString(record);
            redisTemplate.opsForValue().set(redisKey, value, ttl);
            
            log.debug("Operation completed: {}", idempotencyKey);
            
        } catch (Exception e) {
            log.error("Error completing operation for key: {}", idempotencyKey, e);
        }
    }

    /**
     * Marks an operation as failed.
     * 
     * @param idempotencyKey Unique key for the operation
     * @param operationId Unique identifier for this operation instance
     * @param error Error information
     */
    @Timed("idempotency.fail")
    public void failOperation(String idempotencyKey, UUID operationId, String error) {
        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;
        
        try {
            IdempotencyRecord record = IdempotencyRecord.builder()
                .idempotencyKey(idempotencyKey)
                .operationId(operationId)
                .status(IdempotencyStatus.FAILED)
                .error(error)
                .createdAt(Instant.now())
                .completedAt(Instant.now())
                .build();
            
            String value = objectMapper.writeValueAsString(record);
            redisTemplate.opsForValue().set(redisKey, value, Duration.ofMinutes(30)); // Shorter TTL for failures
            
            log.debug("Operation failed: {}", idempotencyKey);
            
        } catch (Exception e) {
            log.error("Error marking operation as failed for key: {}", idempotencyKey, e);
        }
    }

    /**
     * Removes an idempotency record (use with caution).
     * 
     * @param idempotencyKey Unique key for the operation
     */
    public void removeIdempotencyRecord(String idempotencyKey) {
        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;
        redisTemplate.delete(redisKey);
        log.debug("Idempotency record removed: {}", idempotencyKey);
    }

    /**
     * Execute operation with idempotency protection using functional approach.
     * This is the primary method that should be used for idempotent operations.
     * 
     * @param idempotencyKey Unique key for the operation
     * @param operation The operation to execute
     * @return The result of the operation (either fresh execution or cached result)
     */
    @Transactional
    @Timed("idempotency.execute")
    public <T> T executeIdempotent(String idempotencyKey, Supplier<T> operation) {
        return executeIdempotent(idempotencyKey, operation, DEFAULT_TTL);
    }

    /**
     * ENHANCED: Execute operation with idempotency protection, persistent storage, and audit trail
     * 
     * @param serviceName Name of the calling service
     * @param operationType Type of operation being performed
     * @param idempotencyKey Unique key for the operation
     * @param operation The operation to execute
     * @param ttl Time to live for the idempotency record
     * @return The result of the operation (either fresh execution or cached result)
     */
    @Transactional
    @Timed("idempotency.execute")
    @SuppressWarnings("unchecked")
    public <T> T executeIdempotentWithPersistence(String serviceName, String operationType, 
            String idempotencyKey, Supplier<T> operation, Duration ttl) {
        Assert.hasText(idempotencyKey, "Idempotency key cannot be null or empty");
        Assert.hasText(serviceName, "Service name cannot be null or empty");
        Assert.hasText(operationType, "Operation type cannot be null or empty");
        Assert.notNull(operation, "Operation cannot be null");
        Assert.notNull(ttl, "TTL cannot be null");
        
        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;
        UUID operationId = UUID.randomUUID();
        
        try {
            // CRITICAL ENHANCEMENT: Check persistent storage first (survives service restarts)
            Optional<IdempotencyRecord> persistentRecord = idempotencyRepository
                    .findByIdempotencyKeyWithLock(idempotencyKey);
            
            if (persistentRecord.isPresent()) {
                IdempotencyRecord record = persistentRecord.get();
                
                // Clean up expired records
                if (record.isExpired()) {
                    record.markExpired();
                    idempotencyRepository.save(record);
                    log.debug("Expired idempotency record cleaned up: {}", idempotencyKey);
                } else {
                    switch (record.getStatus()) {
                        case COMPLETED:
                            log.info("SECURITY: Returning cached result from persistent storage for key: {}", 
                                    idempotencyKey);
                            return (T) objectMapper.readValue(record.getResult(), Object.class);
                            
                        case IN_PROGRESS:
                            // Check if it's been too long (potential stuck operation)
                            if (record.getCreatedAt().isBefore(Instant.now().minus(ttl))) {
                                log.warn("SECURITY: Stale in-progress operation detected, resetting: {}", 
                                        idempotencyKey);
                                record.markFailed("Operation timeout - resetting");
                                idempotencyRepository.save(record);
                            } else {
                                return waitForCompletion(idempotencyKey, operation, ttl);
                            }
                            break;
                            
                        case FAILED:
                        case RETRYABLE_FAILED:
                            log.debug("Previous operation failed, allowing retry for key: {}", idempotencyKey);
                            break;
                            
                        case EXPIRED:
                        case CANCELLED:
                            log.debug("Previous operation expired/cancelled, allowing retry for key: {}", 
                                    idempotencyKey);
                            break;
                    }
                }
            }
            
            // Check Redis for fast access (if persistent storage allows operation)
            String existingValue = redisTemplate.opsForValue().get(redisKey);
            if (existingValue != null) {
                IdempotencyRecord cacheRecord = objectMapper.readValue(existingValue, IdempotencyRecord.class);
                
                if (cacheRecord.getStatus() == IdempotencyStatus.COMPLETED) {
                    log.debug("Returning cached result from Redis for key: {}", idempotencyKey);
                    return (T) objectMapper.readValue(cacheRecord.getResult(), Object.class);
                }
            }
            
            // CRITICAL: Create persistent record FIRST, then cache record
            IdempotencyRecord newRecord = createPersistentRecord(
                    idempotencyKey, operationId, serviceName, operationType, ttl);
            
            // Mark operation as in progress in Redis for fast access
            if (!startOperation(idempotencyKey, operationId, ttl)) {
                return waitForCompletion(idempotencyKey, operation, ttl);
            }
            
            try {
                log.info("SECURITY: Executing operation with persistent idempotency protection: {}", 
                        idempotencyKey);
                T result = operation.get();
                
                // CRITICAL: Update persistent storage FIRST, then Redis
                String resultJson = objectMapper.writeValueAsString(result);
                newRecord.markCompleted(resultJson);
                idempotencyRepository.save(newRecord);
                
                // Update Redis cache
                completeOperation(idempotencyKey, operationId, result, ttl);
                
                log.info("SECURITY: Operation completed successfully with persistent storage: {}", 
                        idempotencyKey);
                return result;
                
            } catch (Exception operationException) {
                // CRITICAL: Mark failed in persistent storage FIRST
                newRecord.markFailed(operationException.getMessage());
                idempotencyRepository.save(newRecord);
                
                // Update Redis cache
                failOperation(idempotencyKey, operationId, operationException.getMessage());
                
                log.error("SECURITY: Operation failed and recorded in persistent storage: {}", 
                        idempotencyKey, operationException);
                throw operationException;
            }
            
        } catch (Exception e) {
            log.error("CRITICAL: Error in persistent idempotent execution for key: {}", 
                    idempotencyKey, e);
            throw new RuntimeException("Idempotent operation failed", e);
        }
    }

    /**
     * Legacy method for backward compatibility - now uses persistent storage
     */
    @Transactional
    @Timed("idempotency.execute")
    @SuppressWarnings("unchecked")
    public <T> T executeIdempotent(String idempotencyKey, Supplier<T> operation, Duration ttl) {
        // Default service context for legacy calls
        return executeIdempotentWithPersistence("legacy-service", "unknown-operation", 
                idempotencyKey, operation, ttl);
    }
    
    /**
     * Create persistent idempotency record with full audit context
     */
    private IdempotencyRecord createPersistentRecord(String idempotencyKey, UUID operationId, 
            String serviceName, String operationType, Duration ttl) {
        
        // Get security context for audit trail
        String userId = securityContextService.getCurrentUserId();
        String sessionId = securityContextService.getCurrentSessionId();
        String correlationId = securityContextService.getCorrelationId();
        String clientIpAddress = securityContextService.getClientIpAddress();
        String userAgent = securityContextService.getUserAgent();
        
        IdempotencyRecord record = IdempotencyRecord.builder()
                .idempotencyKey(idempotencyKey)
                .operationId(operationId)
                .serviceName(serviceName)
                .operationType(operationType)
                .status(IdempotencyStatus.IN_PROGRESS)
                .correlationId(correlationId)
                .userId(userId)
                .sessionId(sessionId)
                .clientIpAddress(clientIpAddress)
                .userAgent(userAgent)
                .expiresAt(LocalDateTime.now().plus(ttl))
                .build();
        
        return idempotencyRepository.save(record);
    }
    
    /**
     * Wait for an in-progress operation to complete with exponential backoff.
     */
    @SuppressWarnings("unchecked")
    private <T> T waitForCompletion(String idempotencyKey, Supplier<T> fallbackOperation, Duration maxWait) {
        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;
        long maxWaitMs = maxWait.toMillis();
        long startTime = System.currentTimeMillis();
        long waitTime = 50; // Start with 50ms
        
        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            try {
                Thread.sleep(waitTime);
                
                String value = redisTemplate.opsForValue().get(redisKey);
                if (value != null) {
                    IdempotencyRecord record = objectMapper.readValue(value, IdempotencyRecord.class);
                    
                    if (record.getStatus() == IdempotencyStatus.COMPLETED) {
                        log.debug("Operation completed while waiting for key: {}", idempotencyKey);
                        return (T) objectMapper.readValue(record.getResult(), Object.class);
                    } else if (record.getStatus() == IdempotencyStatus.FAILED) {
                        log.debug("Operation failed while waiting, retrying for key: {}", idempotencyKey);
                        return executeIdempotent(idempotencyKey, fallbackOperation, maxWait);
                    }
                } else {
                    // Record disappeared, try executing again
                    return executeIdempotent(idempotencyKey, fallbackOperation, maxWait);
                }
                
                // Exponential backoff with jitter
                waitTime = Math.min(waitTime * 2 + ThreadLocalRandom.current().nextLong(0, waitTime / 2), 5000);
                
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for idempotent operation", ie);
            } catch (Exception e) {
                log.warn("Error while waiting for operation completion: {}", e.getMessage());
                break;
            }
        }
        
        log.warn("Timeout waiting for operation completion, executing fallback for key: {}", idempotencyKey);
        return fallbackOperation.get();
    }
    
    /**
     * Get operation status for monitoring and debugging.
     */
    public Optional<IdempotencyRecord> getOperationStatus(String idempotencyKey) {
        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;
        
        try {
            String value = redisTemplate.opsForValue().get(redisKey);
            if (value != null) {
                return Optional.of(objectMapper.readValue(value, IdempotencyRecord.class));
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error getting operation status for key: {}", idempotencyKey, e);
            return Optional.empty();
        }
    }

    public boolean tryAcquire(String key, Duration duration) {
        return false;
//        TODO - properly implement with business logic, production-ready code, etc. added by aniix october, 28th 2025
    }

    /**
     * PRODUCTION FIX: Get cached result for idempotency key
     * Used by IdempotentKafkaConsumer
     */
    @Timed("idempotency.get_result")
    public <R> Optional<R> getResult(String idempotencyKey) {
        return checkIdempotency(idempotencyKey, Object.class)
            .map(result -> (R) result.getResult());
    }

    /**
     * PRODUCTION FIX: Mark operation as in progress
     * Used by IdempotentKafkaConsumer
     */
    @Timed("idempotency.mark_in_progress")
    public void markInProgress(String idempotencyKey, Duration ttl) {
        startOperation(idempotencyKey, UUID.randomUUID(), ttl);
    }

    /**
     * PRODUCTION FIX: Store operation result
     * Used by IdempotentKafkaConsumer
     */
    @Timed("idempotency.store_result")
    public <R> void storeResult(String idempotencyKey, R result, Duration ttl) {
        // PRODUCTION FIX: completeOperation needs UUID, generate one
        completeOperation(idempotencyKey, UUID.randomUUID(), result, ttl);
    }

    /**
     * Utility methods for generating idempotency keys for financial operations.
     */
    public static class FinancialIdempotencyKeys {
        
        public static String paymentRequest(UUID userId, String merchantTransactionId) {
            return String.format("payment:%s:%s", userId, merchantTransactionId);
        }
        
        public static String walletTransfer(UUID fromWalletId, UUID toWalletId, String amount, String clientTransactionId) {
            return String.format("transfer:%s:%s:%s:%s", fromWalletId, toWalletId, amount, clientTransactionId);
        }
        
        public static String deposit(UUID walletId, String amount, String externalTransactionId) {
            return String.format("deposit:%s:%s:%s", walletId, amount, externalTransactionId);
        }
        
        public static String withdrawal(UUID walletId, String amount, String externalTransactionId) {
            return String.format("withdrawal:%s:%s:%s", walletId, amount, externalTransactionId);
        }
        
        public static String refund(UUID originalPaymentId, String amount) {
            return String.format("refund:%s:%s", originalPaymentId, amount);
        }
        
        public static String ledgerEntry(String accountId, String amount, String transactionType, String reference) {
            return String.format("ledger:%s:%s:%s:%s", accountId, amount, transactionType, reference);
        }
        
        public static String kycVerification(String userId, String requestId) {
            return String.format("kyc:%s:%s", userId, requestId);
        }
        
        public static String sanctionsScreening(String userId, String requestId) {
            return String.format("sanctions:%s:%s", userId, requestId);
        }
        
        public static String documentUpload(String userId, String verificationId, String documentType) {
            return String.format("document:%s:%s:%s", userId, verificationId, documentType);
        }
        
        public static String userRegistration(String email, String phoneNumber) {
            return String.format("registration:%s:%s", email, phoneNumber);
        }
        
        public static String deviceRegistration(String userId, String deviceFingerprint) {
            return String.format("device:%s:%s", userId, deviceFingerprint);
        }
    }

    /**
     * Validates idempotency key format
     *
     * @param key The idempotency key to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            return false;
        }

        // Key must be 1-255 characters, alphanumeric, hyphens, or underscores
        int length = key.length();
        if (length < 1 || length > 255) {
            return false;
        }

        // Check for valid characters: alphanumeric, hyphen, underscore
        return key.matches("^[a-zA-Z0-9_-]+$");
    }
}