package com.waqiti.saga.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.saga.SagaStatus;
import com.waqiti.saga.domain.Saga;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Saga Repository implementation with multi-tier persistence:
 * L1: In-memory cache for active sagas
 * L2: Redis for fast recovery
 * L3: PostgreSQL for durability
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SagaRepositoryImpl implements SagaRepository {
    
    private final JdbcTemplate jdbcTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    // In-memory cache for active sagas (L1 cache)
    private final Map<String, Saga> activeCache = new ConcurrentHashMap<>();
    
    // Configuration
    private static final int CHECKPOINT_INTERVAL_STEPS = 3;
    private static final int MAX_CHECKPOINTS_PER_SAGA = 10;
    private static final int RECOVERY_WINDOW_HOURS = 24;
    private static final String REDIS_SAGA_PREFIX = "saga:state:";
    private static final String REDIS_LOCK_PREFIX = "saga:lock:";
    
    @Override
    @Transactional
    public Saga save(Saga saga) {
        String sagaId = saga.getSagaId();
        log.debug("Saving saga: {}", sagaId);
        
        try {
            // Update version for optimistic locking
            saga.setVersion(saga.getVersion() + 1);
            saga.setUpdatedAt(Instant.now());
            
            // L1: Update in-memory cache
            activeCache.put(sagaId, saga);
            
            // L2: Save to Redis for fast recovery
            saveToRedis(saga);
            
            // L3: Persist to database for durability
            persistToDatabase(saga);
            
            // Create checkpoint if needed
            if (shouldCreateCheckpoint(saga)) {
                createCheckpoint(saga);
            }
            
            log.debug("Successfully saved saga: {}", sagaId);
            return saga;
            
        } catch (Exception e) {
            log.error("Failed to save saga: {}", sagaId, e);
            throw new SagaPersistenceException("Failed to save saga", e);
        }
    }
    
    @Override
    public Saga update(Saga saga) {
        return save(saga); // Save handles both insert and update
    }
    
    @Override
    public Optional<Saga> findById(String sagaId) {
        log.debug("Finding saga: {}", sagaId);
        
        try {
            // L1: Check in-memory cache first
            Saga saga = activeCache.get(sagaId);
            if (saga != null) {
                return Optional.of(saga);
            }
            
            // L2: Try Redis
            saga = loadFromRedis(sagaId);
            if (saga != null) {
                activeCache.put(sagaId, saga);
                return Optional.of(saga);
            }
            
            // L3: Load from database
            saga = loadFromDatabase(sagaId);
            if (saga != null) {
                // Restore to caches
                activeCache.put(sagaId, saga);
                saveToRedis(saga);
                return Optional.of(saga);
            }
            
            return Optional.empty();
            
        } catch (Exception e) {
            log.error("Failed to find saga: {}", sagaId, e);
            return Optional.empty();
        }
    }
    
    @Override
    public List<Saga> findByStatus(String status) {
        String sql = """
            SELECT * FROM sagas 
            WHERE status = ? 
            ORDER BY updated_at DESC
        """;
        
        try {
            return jdbcTemplate.query(sql, new Object[]{status}, this::mapSagaFromResultSet);
        } catch (Exception e) {
            log.error("Failed to find sagas by status: {}", status, e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Saga> findExpiredSagas() {
        String sql = """
            SELECT * FROM sagas 
            WHERE expires_at < ? 
            AND status NOT IN ('COMPLETED', 'COMPENSATED', 'FAILED')
        """;
        
        try {
            return jdbcTemplate.query(sql, new Object[]{Instant.now()}, this::mapSagaFromResultSet);
        } catch (Exception e) {
            log.error("Failed to find expired sagas", e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public void delete(String sagaId) {
        log.info("Deleting saga: {}", sagaId);
        
        try {
            // Remove from all layers
            activeCache.remove(sagaId);
            redisTemplate.delete(REDIS_SAGA_PREFIX + sagaId);
            jdbcTemplate.update("DELETE FROM sagas WHERE saga_id = ?", sagaId);
            
        } catch (Exception e) {
            log.error("Failed to delete saga: {}", sagaId, e);
        }
    }
    
    @Override
    public boolean exists(String sagaId) {
        if (activeCache.containsKey(sagaId)) {
            return true;
        }
        
        String sql = "SELECT COUNT(*) FROM sagas WHERE saga_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, sagaId);
        return count != null && count > 0;
    }
    
    @Override
    public List<Saga> findSagasForRecovery() {
        String sql = """
            SELECT * FROM sagas 
            WHERE status IN ('IN_PROGRESS', 'COMPENSATING')
            AND updated_at < ?
            ORDER BY updated_at ASC
            LIMIT 100
        """;
        
        try {
            // Find sagas that haven't been updated in the last 5 minutes
            Instant cutoff = Instant.now().minus(5, ChronoUnit.MINUTES);
            return jdbcTemplate.query(sql, new Object[]{cutoff}, this::mapSagaFromResultSet);
            
        } catch (Exception e) {
            log.error("Failed to find sagas for recovery", e);
            return new ArrayList<>();
        }
    }
    
    @Override
    @Transactional
    public void createCheckpoint(Saga saga) {
        log.info("Creating checkpoint for saga: {}", saga.getSagaId());
        
        String sql = """
            INSERT INTO saga_checkpoints
            (checkpoint_id, saga_id, saga_type, step_index, status, 
             saga_data, compensation_data, created_at)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
        """;
        
        try {
            String checkpointId = UUID.randomUUID().toString();
            String sagaDataJson = objectMapper.writeValueAsString(saga.getSagaData());
            String compensationDataJson = objectMapper.writeValueAsString(saga.getCompensationData());
            
            jdbcTemplate.update(sql,
                checkpointId,
                saga.getSagaId(),
                saga.getSagaType(),
                saga.getStepIndex(),
                saga.getStatus(),
                sagaDataJson,
                compensationDataJson,
                Instant.now()
            );
            
            // Clean up old checkpoints
            cleanupOldCheckpoints(saga.getSagaId());
            
        } catch (Exception e) {
            log.error("Failed to create checkpoint for saga: {}", saga.getSagaId(), e);
        }
    }
    
    @Override
    public Optional<Saga> recoverFromCheckpoint(String sagaId) {
        log.info("Recovering saga from checkpoint: {}", sagaId);
        
        String sql = """
            SELECT * FROM saga_checkpoints
            WHERE saga_id = ?
            ORDER BY created_at DESC
            LIMIT 1
        """;
        
        try {
            return Optional.ofNullable(
                jdbcTemplate.queryForObject(sql, new Object[]{sagaId}, (rs, rowNum) -> {
                    // Load the original saga first
                    Saga saga = loadFromDatabase(sagaId);
                    if (saga != null) {
                        // Restore checkpoint data
                        String sagaDataJson = rs.getString("saga_data");
                        String compensationDataJson = rs.getString("compensation_data");
                        
                        if (sagaDataJson != null) {
                            saga.setSagaData(objectMapper.readValue(sagaDataJson, Map.class));
                        }
                        if (compensationDataJson != null) {
                            saga.setCompensationData(objectMapper.readValue(compensationDataJson, Map.class));
                        }
                        
                        saga.setStepIndex(rs.getInt("step_index"));
                        saga.setStatus(rs.getString("status"));
                        
                        log.info("Successfully recovered saga {} from checkpoint", sagaId);
                        return saga;
                    }
                    return null;
                })
            );
            
        } catch (Exception e) {
            log.error("Failed to recover saga from checkpoint: {}", sagaId, e);
            return Optional.empty();
        }
    }
    
    @Override
    public boolean verifyIntegrity(String sagaId) {
        try {
            Saga saga = loadFromDatabase(sagaId);
            if (saga == null) {
                return false;
            }
            
            String storedChecksum = getStoredChecksum(sagaId);
            String currentChecksum = calculateChecksum(saga);
            
            return currentChecksum.equals(storedChecksum);
            
        } catch (Exception e) {
            log.error("Failed to verify saga integrity: {}", sagaId, e);
            return false;
        }
    }
    
    @Override
    public boolean acquireLock(String sagaId, long timeoutSeconds) {
        String lockKey = REDIS_LOCK_PREFIX + sagaId;
        String lockValue = UUID.randomUUID().toString();
        
        try {
            Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, timeoutSeconds, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(acquired);
            
        } catch (Exception e) {
            log.error("Failed to acquire lock for saga: {}", sagaId, e);
            return false;
        }
    }
    
    @Override
    public void releaseLock(String sagaId) {
        String lockKey = REDIS_LOCK_PREFIX + sagaId;
        
        try {
            redisTemplate.delete(lockKey);
        } catch (Exception e) {
            log.error("Failed to release lock for saga: {}", sagaId, e);
        }
    }
    
    // Private helper methods
    
    private void saveToRedis(Saga saga) {
        try {
            String key = REDIS_SAGA_PREFIX + saga.getSagaId();
            String json = objectMapper.writeValueAsString(saga);
            
            // Calculate TTL based on expiration
            long ttl = 24 * 60 * 60; // 24 hours default
            if (saga.getExpiresAt() != null) {
                long expirationSeconds = ChronoUnit.SECONDS.between(Instant.now(), saga.getExpiresAt());
                ttl = Math.max(ttl, expirationSeconds + (24 * 60 * 60));
            }
            
            redisTemplate.opsForValue().set(key, json, ttl, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            log.warn("Failed to save saga to Redis: {}", saga.getSagaId(), e);
        }
    }
    
    private Saga loadFromRedis(String sagaId) {
        try {
            String key = REDIS_SAGA_PREFIX + sagaId;
            Object json = redisTemplate.opsForValue().get(key);
            
            if (json != null) {
                return objectMapper.readValue(json.toString(), Saga.class);
            }
            
        } catch (Exception e) {
            log.warn("Failed to load saga from Redis: {}", sagaId, e);
        }
        
        return null; // This null is acceptable as it's checked by caller
    }
    
    private void persistToDatabase(Saga saga) {
        String sql = """
            INSERT INTO sagas 
            (saga_id, saga_type, status, current_step, step_index, retry_count,
             saga_data, compensation_data, created_at, updated_at, expires_at,
             version, checksum)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?)
            ON CONFLICT (saga_id) DO UPDATE SET
                status = EXCLUDED.status,
                current_step = EXCLUDED.current_step,
                step_index = EXCLUDED.step_index,
                retry_count = EXCLUDED.retry_count,
                saga_data = EXCLUDED.saga_data,
                compensation_data = EXCLUDED.compensation_data,
                updated_at = EXCLUDED.updated_at,
                version = EXCLUDED.version,
                checksum = EXCLUDED.checksum
        """;
        
        try {
            String sagaDataJson = objectMapper.writeValueAsString(saga.getSagaData());
            String compensationDataJson = objectMapper.writeValueAsString(saga.getCompensationData());
            String checksum = calculateChecksum(saga);
            
            jdbcTemplate.update(sql,
                saga.getSagaId(),
                saga.getSagaType(),
                saga.getStatus(),
                saga.getCurrentStep(),
                saga.getStepIndex(),
                saga.getRetryCount(),
                sagaDataJson,
                compensationDataJson,
                saga.getCreatedAt(),
                saga.getUpdatedAt(),
                saga.getExpiresAt(),
                saga.getVersion(),
                checksum
            );
            
        } catch (Exception e) {
            log.error("Failed to persist saga to database: {}", saga.getSagaId(), e);
            throw new SagaPersistenceException("Failed to persist saga", e);
        }
    }
    
    private Saga loadFromDatabase(String sagaId) {
        String sql = """
            SELECT saga_id, saga_type, status, current_step, step_index, retry_count,
                   saga_data, compensation_data, created_at, updated_at, expires_at,
                   version, checksum
            FROM sagas
            WHERE saga_id = ?
        """;
        
        try {
            List<Saga> results = jdbcTemplate.query(sql, new Object[]{sagaId}, this::mapSagaFromResultSet);
            if (results.isEmpty()) {
                log.debug("No saga found in database for: {}", sagaId);
                return null; // This null is acceptable as it's checked by caller
            }
            return results.get(0);
        } catch (Exception e) {
            log.error("Database error loading saga: {}", sagaId, e);
            throw new SagaPersistenceException("Failed to load saga from database", e);
        }
    }
    
    private Saga mapSagaFromResultSet(ResultSet rs, int rowNum) throws SQLException {
        Saga saga = new Saga(
            rs.getString("saga_type"),
            rs.getString("saga_id")
        );
        
        saga.setStatus(rs.getString("status"));
        saga.setCurrentStep(rs.getString("current_step"));
        saga.setStepIndex(rs.getInt("step_index"));
        saga.setRetryCount(rs.getInt("retry_count"));
        saga.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        saga.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
        saga.setVersion(rs.getLong("version"));
        
        // Parse JSON data
        try {
            String sagaDataJson = rs.getString("saga_data");
            String compensationDataJson = rs.getString("compensation_data");
            
            if (sagaDataJson != null) {
                saga.setSagaData(objectMapper.readValue(sagaDataJson, Map.class));
            }
            if (compensationDataJson != null) {
                saga.setCompensationData(objectMapper.readValue(compensationDataJson, Map.class));
            }
        } catch (Exception e) {
            log.error("Failed to parse saga JSON data", e);
        }
        
        if (rs.getTimestamp("expires_at") != null) {
            saga.setExpiresAt(rs.getTimestamp("expires_at").toInstant());
        }
        
        return saga;
    }
    
    private boolean shouldCreateCheckpoint(Saga saga) {
        return saga.getStepIndex() % CHECKPOINT_INTERVAL_STEPS == 0 ||
               SagaStatus.COMPENSATING.name().equals(saga.getStatus()) ||
               SagaStatus.COMPLETED.name().equals(saga.getStatus());
    }
    
    private void cleanupOldCheckpoints(String sagaId) {
        String sql = """
            DELETE FROM saga_checkpoints
            WHERE saga_id = ? AND checkpoint_id NOT IN (
                SELECT checkpoint_id FROM saga_checkpoints
                WHERE saga_id = ?
                ORDER BY created_at DESC
                LIMIT ?
            )
        """;
        
        try {
            jdbcTemplate.update(sql, sagaId, sagaId, MAX_CHECKPOINTS_PER_SAGA);
        } catch (Exception e) {
            log.warn("Failed to cleanup old checkpoints for saga: {}", sagaId, e);
        }
    }
    
    private String calculateChecksum(Saga saga) {
        try {
            String data = saga.getSagaId() + saga.getStatus() + 
                         saga.getCurrentStep() + saga.getStepIndex() + 
                         saga.getVersion();
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
            
        } catch (Exception e) {
            log.warn("Failed to calculate checksum for saga: {}", saga.getSagaId(), e);
            return "CHECKSUM_ERROR";
        }
    }
    
    private String getStoredChecksum(String sagaId) {
        String sql = "SELECT checksum FROM sagas WHERE saga_id = ?";
        return jdbcTemplate.queryForObject(sql, String.class, sagaId);
    }
    
    /**
     * Clean up expired sagas periodically
     */
    @Scheduled(cron = "0 0 * * * ?") // Every hour
    public void cleanupExpiredSagas() {
        log.info("Starting cleanup of expired sagas");
        
        try {
            // Remove from memory cache
            Instant cutoff = Instant.now().minus(RECOVERY_WINDOW_HOURS, ChronoUnit.HOURS);
            activeCache.entrySet().removeIf(entry -> {
                Saga saga = entry.getValue();
                return saga.getExpiresAt() != null && saga.getExpiresAt().isBefore(cutoff);
            });
            
            // Clean database
            String sql = "DELETE FROM sagas WHERE expires_at < ? AND status IN ('COMPLETED', 'COMPENSATED', 'FAILED')";
            int deleted = jdbcTemplate.update(sql, cutoff);
            
            log.info("Cleaned up {} expired sagas", deleted);
            
        } catch (Exception e) {
            log.error("Failed to cleanup expired sagas", e);
        }
    }
    
    public static class SagaPersistenceException extends RuntimeException {
        public SagaPersistenceException(String message) {
            super(message);
        }
        
        public SagaPersistenceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}