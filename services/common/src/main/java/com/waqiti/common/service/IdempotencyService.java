package com.waqiti.common.service;

import com.waqiti.common.idempotency.IdempotencyRecord;
import com.waqiti.common.idempotency.IdempotencyResult;
import com.waqiti.common.idempotency.IdempotencyStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Idempotency Service
 * 
 * Provides idempotency guarantees for API operations to prevent duplicate processing
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    
    /**
     * Execute operation with idempotency guarantee
     */
    public <T> IdempotencyResult<T> executeIdempotent(
            String idempotencyKey, 
            Supplier<T> operation,
            Duration ttl) {
        
        String fullKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        
        try {
            // Check if operation already exists
            IdempotencyRecord existingRecord = getExistingRecord(fullKey);
            
            if (existingRecord != null) {
                return handleExistingRecord(existingRecord);
            }
            
            // Create new record in PROCESSING state
            IdempotencyRecord processingRecord = createProcessingRecord(idempotencyKey);
            storeRecord(fullKey, processingRecord, ttl);
            
            try {
                // Execute the operation
                T result = operation.get();
                
                // Update record with success
                IdempotencyRecord successRecord = IdempotencyRecord.builder()
                    .idempotencyKey(processingRecord.getIdempotencyKey())
                    .operationId(processingRecord.getOperationId())
                    .status(IdempotencyStatus.COMPLETED)
                    .result(result != null ? result.toString() : null)
                    .createdAt(processingRecord.getCreatedAt())
                    .completedAt(java.time.Instant.now())
                    .build();
                
                storeRecord(fullKey, successRecord, ttl);
                
                return IdempotencyResult.<T>builder()
                    .status(IdempotencyStatus.COMPLETED)
                    .result(result)
                    .operationId(successRecord.getOperationId())
                    .isNewExecution(true)
                    .build();
                
            } catch (Exception e) {
                // Update record with failure
                IdempotencyRecord failureRecord = IdempotencyRecord.builder()
                    .idempotencyKey(processingRecord.getIdempotencyKey())
                    .operationId(processingRecord.getOperationId())
                    .status(IdempotencyStatus.FAILED)
                    .error(e.getMessage())
                    .createdAt(processingRecord.getCreatedAt())
                    .completedAt(java.time.Instant.now())
                    .build();
                
                storeRecord(fullKey, failureRecord, ttl);
                throw e;
            }
            
        } catch (Exception e) {
            log.error("Error in idempotent execution for key: {}", idempotencyKey, e);
            return IdempotencyResult.<T>builder()
                .status(IdempotencyStatus.FAILED)
                .operationId(java.util.UUID.randomUUID())
                .error(e.getMessage())
                .isNewExecution(true)
                .build();
        }
    }
    
    /**
     * Execute operation with default TTL
     */
    public <T> IdempotencyResult<T> executeIdempotent(
            String idempotencyKey, 
            Supplier<T> operation) {
        return executeIdempotent(idempotencyKey, operation, DEFAULT_TTL);
    }
    
    /**
     * Check if operation is already processed
     */
    public boolean isProcessed(String idempotencyKey) {
        String fullKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        IdempotencyRecord record = getExistingRecord(fullKey);
        return record != null && record.getStatus() == IdempotencyStatus.COMPLETED;
    }
    
    /**
     * Get operation result if already processed
     */
    @SuppressWarnings("unchecked")
    public <T> T getResult(String idempotencyKey, Class<T> resultType) {
        String fullKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        IdempotencyRecord record = getExistingRecord(fullKey);
        
        if (record != null && record.getStatus() == IdempotencyStatus.COMPLETED) {
            return (T) record.getResult();
        }
        
        return null;
    }
    
    /**
     * Delete idempotency record
     */
    public void deleteRecord(String idempotencyKey) {
        String fullKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        redisTemplate.delete(fullKey);
        log.debug("Deleted idempotency record for key: {}", idempotencyKey);
    }
    
    /**
     * Get operation status
     */
    public IdempotencyStatus getStatus(String idempotencyKey) {
        String fullKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        IdempotencyRecord record = getExistingRecord(fullKey);
        return record != null ? record.getStatus() : null;
    }
    
    // Private helper methods
    
    private IdempotencyRecord getExistingRecord(String fullKey) {
        try {
            return (IdempotencyRecord) redisTemplate.opsForValue().get(fullKey);
        } catch (Exception e) {
            log.warn("Error retrieving idempotency record for key: {}", fullKey, e);
            return null;
        }
    }
    
    @SuppressWarnings("unchecked")
    private <T> IdempotencyResult<T> handleExistingRecord(IdempotencyRecord record) {
        switch (record.getStatus()) {
            case COMPLETED:
                return IdempotencyResult.<T>builder()
                    .status(IdempotencyStatus.COMPLETED)
                    .result((T) record.getResult())
                    .operationId(record.getOperationId())
                    .isNewExecution(false)
                    .build();
                
            case IN_PROGRESS:
                return IdempotencyResult.<T>builder()
                    .status(IdempotencyStatus.IN_PROGRESS)
                    .operationId(record.getOperationId())
                    .isNewExecution(false)
                    .build();
                
            case FAILED:
                return IdempotencyResult.<T>builder()
                    .status(IdempotencyStatus.FAILED)
                    .operationId(record.getOperationId())
                    .error(record.getError())
                    .isNewExecution(false)
                    .build();
                
            default:
                throw new IllegalStateException("Unknown idempotency status: " + record.getStatus());
        }
    }
    
    private IdempotencyRecord createProcessingRecord(String idempotencyKey) {
        return IdempotencyRecord.builder()
            .idempotencyKey(idempotencyKey)
            .operationId(java.util.UUID.randomUUID())
            .status(IdempotencyStatus.IN_PROGRESS)
            .createdAt(java.time.Instant.now())
            .build();
    }
    
    private void storeRecord(String fullKey, IdempotencyRecord record, Duration ttl) {
        redisTemplate.opsForValue().set(fullKey, record, ttl.getSeconds(), TimeUnit.SECONDS);
    }

    /**
     * PRODUCTION FIX: Check if already processed (2-param version)
     * Used by SystemAlertsDlqConsumer
     */
    public boolean isAlreadyProcessed(String key1, String key2) {
        String idempotencyKey = key1 + ":" + key2;
        return isProcessed(idempotencyKey);
    }

    /**
     * PRODUCTION FIX: Mark as processed with result (3-param version)
     * Used by SystemAlertsDlqConsumer
     */
    public <T> void markAsProcessed(String key1, String key2, T result) {
        String idempotencyKey = key1 + ":" + key2;
        String fullKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;

        IdempotencyRecord record = IdempotencyRecord.builder()
            .idempotencyKey(idempotencyKey)
            .operationId(java.util.UUID.randomUUID())
            .status(IdempotencyStatus.COMPLETED)
            .result(result != null ? result.toString() : null)
            .createdAt(java.time.Instant.now())
            .completedAt(java.time.Instant.now())
            .build();

        storeRecord(fullKey, record, DEFAULT_TTL);
    }
}