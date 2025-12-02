package com.waqiti.payment.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.Auditable;
import com.waqiti.payment.exception.DuplicateRequestException;
import com.waqiti.payment.exception.IdempotencyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing idempotency keys to prevent duplicate payment operations
 * Ensures exactly-once semantics for critical financial transactions
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyKeyService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final ObjectMapper objectMapper;
    
    private static final String KEY_PREFIX = "idempotency:";
    private static final String LOCK_PREFIX = "idempotency:lock:";
    
    @Value("${idempotency.ttl-hours:24}")
    private int ttlHours;
    
    @Value("${idempotency.lock-timeout-seconds:30}")
    private int lockTimeoutSeconds;
    
    @Value("${idempotency.enable-persistence:true}")
    private boolean enablePersistence;

    /**
     * Generate a new idempotency key
     */
    public String generateKey() {
        return UUID.randomUUID().toString();
    }

    /**
     * Generate idempotency key with prefix
     */
    public String generateKey(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString();
    }

    /**
     * Check if an idempotency key exists and return cached response if available
     */
    public Optional<IdempotencyRecord> checkKey(String key) {
        if (key == null || key.isEmpty()) {
            return Optional.empty();
        }
        
        // Check Redis cache first
        String cacheKey = KEY_PREFIX + key;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        
        if (cached != null) {
            log.debug("Found cached idempotency record for key: {}", key);
            return Optional.of((IdempotencyRecord) cached);
        }
        
        // Check database if persistence is enabled
        if (enablePersistence) {
            return idempotencyRepository.findByIdempotencyKey(key);
        }
        
        return Optional.empty();
    }

    /**
     * Acquire lock for idempotency key to prevent concurrent processing
     */
    @Transactional
    public boolean acquireLock(String key, String requestHash) {
        String lockKey = LOCK_PREFIX + key;
        
        // Try to acquire distributed lock
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, requestHash, Duration.ofSeconds(lockTimeoutSeconds));
        
        if (Boolean.TRUE.equals(acquired)) {
            log.debug("Acquired idempotency lock for key: {}", key);
            return true;
        }
        
        // Check if we already own the lock (same request hash)
        String existingHash = (String) redisTemplate.opsForValue().get(lockKey);
        if (requestHash.equals(existingHash)) {
            log.debug("Already own idempotency lock for key: {}", key);
            return true;
        }
        
        log.warn("Failed to acquire idempotency lock for key: {}", key);
        return false;
    }

    /**
     * Release lock for idempotency key
     */
    public void releaseLock(String key) {
        String lockKey = LOCK_PREFIX + key;
        redisTemplate.delete(lockKey);
        log.debug("Released idempotency lock for key: {}", key);
    }

    /**
     * Store idempotency record with response
     */
    @Transactional
    @Auditable(action = "IDEMPOTENCY_STORE")
    public void storeResponse(String key, String requestHash, Object request, Object response, 
                             IdempotencyStatus status) {
        try {
            IdempotencyRecord record = IdempotencyRecord.builder()
                    .idempotencyKey(key)
                    .requestHash(requestHash)
                    .request(objectMapper.writeValueAsString(request))
                    .response(response != null ? objectMapper.writeValueAsString(response) : null)
                    .status(status)
                    .createdAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(ttlHours * 3600))
                    .build();
            
            // Store in Redis with TTL
            String cacheKey = KEY_PREFIX + key;
            redisTemplate.opsForValue().set(cacheKey, record, ttlHours, TimeUnit.HOURS);
            
            // Store in database if persistence is enabled
            if (enablePersistence) {
                idempotencyRepository.save(record);
            }
            
            log.info("Stored idempotency record for key: {} with status: {}", key, status);
            
        } catch (Exception e) {
            log.error("Error storing idempotency record for key: {}", key, e);
            throw new IdempotencyException("Failed to store idempotency record", e);
        }
    }

    /**
     * Process request with idempotency protection
     */
    public <T> T processWithIdempotency(
            String key, 
            Object request, 
            java.util.function.Supplier<T> processor) {
        
        if (key == null || key.isEmpty()) {
            // No idempotency key provided, process normally
            return processor.get();
        }
        
        String requestHash = calculateRequestHash(request);
        
        // Check for existing record
        Optional<IdempotencyRecord> existing = checkKey(key);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            
            // Verify request hash matches
            if (!requestHash.equals(record.getRequestHash())) {
                log.error("Idempotency key {} reused with different request", key);
                throw new DuplicateRequestException(
                    "Idempotency key already used with different request parameters");
            }
            
            // Return cached response based on status
            switch (record.getStatus()) {
                case COMPLETED:
                    log.info("Returning cached response for idempotency key: {}", key);
                    return deserializeResponse(record.getResponse());
                    
                case PROCESSING:
                    log.warn("Request still processing for idempotency key: {}", key);
                    throw new IdempotencyException("Request is still being processed");
                    
                case FAILED:
                    log.info("Retrying failed request for idempotency key: {}", key);
                    break; // Allow retry
                    
                default:
                    break;
            }
        }
        
        // Acquire lock
        if (!acquireLock(key, requestHash)) {
            throw new IdempotencyException("Could not acquire lock for idempotency key: " + key);
        }
        
        try {
            // Store processing status
            storeResponse(key, requestHash, request, null, IdempotencyStatus.PROCESSING);
            
            // Process the request
            T response = processor.get();
            
            // Store successful response
            storeResponse(key, requestHash, request, response, IdempotencyStatus.COMPLETED);
            
            return response;
            
        } catch (Exception e) {
            // Store failure status
            storeResponse(key, requestHash, request, null, IdempotencyStatus.FAILED);
            throw e;
            
        } finally {
            // Release lock
            releaseLock(key);
        }
    }

    /**
     * Validate idempotency key format
     */
    public boolean isValidKey(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        
        // Check key format (UUID or prefixed UUID)
        return key.matches("^[a-zA-Z0-9\\-_]+$") && 
               key.length() >= 32 && 
               key.length() <= 128;
    }

    /**
     * Clean up expired idempotency records
     */
    @Transactional
    public void cleanupExpiredRecords() {
        if (enablePersistence) {
            int deleted = idempotencyRepository.deleteExpiredRecords(Instant.now());
            log.info("Cleaned up {} expired idempotency records", deleted);
        }
    }

    /**
     * Calculate hash of request for comparison
     */
    private String calculateRequestHash(Object request) {
        try {
            String json = objectMapper.writeValueAsString(request);
            return org.apache.commons.codec.digest.DigestUtils.sha256Hex(json);
        } catch (Exception e) {
            log.error("Error calculating request hash", e);
            return UUID.randomUUID().toString();
        }
    }

    /**
     * Deserialize response from JSON
     */
    @SuppressWarnings("unchecked")
    private <T> T deserializeResponse(String json) {
        try {
            return (T) objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            log.error("Error deserializing response", e);
            throw new IdempotencyException("Failed to deserialize cached response", e);
        }
    }

    /**
     * Idempotency record entity
     */
    public static class IdempotencyRecord {
        private String id;
        private String idempotencyKey;
        private String requestHash;
        private String request;
        private String response;
        private IdempotencyStatus status;
        private Instant createdAt;
        private Instant expiresAt;
        private String errorMessage;
        
        // Builder pattern
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private IdempotencyRecord record = new IdempotencyRecord();
            
            public Builder idempotencyKey(String key) {
                record.idempotencyKey = key;
                return this;
            }
            
            public Builder requestHash(String hash) {
                record.requestHash = hash;
                return this;
            }
            
            public Builder request(String request) {
                record.request = request;
                return this;
            }
            
            public Builder response(String response) {
                record.response = response;
                return this;
            }
            
            public Builder status(IdempotencyStatus status) {
                record.status = status;
                return this;
            }
            
            public Builder createdAt(Instant createdAt) {
                record.createdAt = createdAt;
                return this;
            }
            
            public Builder expiresAt(Instant expiresAt) {
                record.expiresAt = expiresAt;
                return this;
            }
            
            public Builder errorMessage(String errorMessage) {
                record.errorMessage = errorMessage;
                return this;
            }
            
            public IdempotencyRecord build() {
                record.id = UUID.randomUUID().toString();
                return record;
            }
        }
        
        // Getters
        public String getId() { return id; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public String getRequestHash() { return requestHash; }
        public String getRequest() { return request; }
        public String getResponse() { return response; }
        public IdempotencyStatus getStatus() { return status; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getExpiresAt() { return expiresAt; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * Idempotency status enum
     */
    public enum IdempotencyStatus {
        PROCESSING,
        COMPLETED,
        FAILED,
        EXPIRED
    }

    /**
     * Repository interface for idempotency records
     */
    public interface IdempotencyRecordRepository {
        Optional<IdempotencyRecord> findByIdempotencyKey(String key);
        IdempotencyRecord save(IdempotencyRecord record);
        int deleteExpiredRecords(Instant cutoffTime);
    }
}