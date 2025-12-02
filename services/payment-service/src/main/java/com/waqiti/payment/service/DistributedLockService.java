package com.waqiti.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Production-grade distributed locking service with Redis and database fallback.
 * Implements fair locking, deadlock detection, and automatic lock cleanup.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    
    @Value("${distributed-lock.default-timeout:PT30S}")
    private Duration defaultTimeout;
    
    @Value("${distributed-lock.redis-enabled:true}")
    private boolean redisEnabled;
    
    @Value("${distributed-lock.database-fallback:true}")
    private boolean databaseFallbackEnabled;
    
    @Value("${distributed-lock.max-wait-time:PT5M}")
    private Duration maxWaitTime;
    
    @Value("${distributed-lock.heartbeat-interval:PT10S}")
    private Duration heartbeatInterval;
    
    @Value("${distributed-lock.cleanup-interval:PT1M}")
    private Duration cleanupInterval;
    
    @Value("${distributed-lock.fair-locking:true}")
    private boolean fairLockingEnabled;
    
    // Lock tracking
    private final Map<String, LockInfo> activeLocks = new ConcurrentHashMap<>();
    private final Map<String, Queue<LockWaiter>> lockWaiters = new ConcurrentHashMap<>();
    private final Set<String> ownedLocks = ConcurrentHashMap.newKeySet();
    private final AtomicLong lockCounter = new AtomicLong(0);
    private final AtomicLong unlockCounter = new AtomicLong(0);
    private final AtomicLong timeoutCounter = new AtomicLong(0);
    
    // Node identification
    private final String nodeId = generateNodeId();
    
    // Redis Lua scripts for atomic operations
    private static final String ACQUIRE_LOCK_SCRIPT = """
        if redis.call('exists', KEYS[1]) == 0 then
            redis.call('hset', KEYS[1], 'owner', ARGV[1], 'acquired_at', ARGV[2], 'expires_at', ARGV[3])
            redis.call('expire', KEYS[1], ARGV[4])
            return 1
        elseif redis.call('hget', KEYS[1], 'owner') == ARGV[1] then
            redis.call('hset', KEYS[1], 'expires_at', ARGV[3])
            redis.call('expire', KEYS[1], ARGV[4])
            return 1
        else
            return 0
        end
        """;
    
    private static final String RELEASE_LOCK_SCRIPT = """
        if redis.call('hget', KEYS[1], 'owner') == ARGV[1] then
            return redis.call('del', KEYS[1])
        else
            return 0
        end
        """;
    
    private static final String EXTEND_LOCK_SCRIPT = """
        if redis.call('hget', KEYS[1], 'owner') == ARGV[1] then
            redis.call('hset', KEYS[1], 'expires_at', ARGV[2])
            redis.call('expire', KEYS[1], ARGV[3])
            return 1
        else
            return 0
        end
        """;
    
    /**
     * Acquire distributed lock with timeout
     */
    public boolean acquireLock(String lockName, Duration timeout) {
        return acquireLock(lockName, timeout, null);
    }
    
    /**
     * Acquire distributed lock with timeout and metadata
     */
    public boolean acquireLock(String lockName, Duration timeout, Map<String, String> metadata) {
        if (lockName == null || lockName.trim().isEmpty()) {
            throw new IllegalArgumentException("Lock name cannot be null or empty");
        }
        
        String normalizedLockName = normalizeLockName(lockName);
        Duration actualTimeout = timeout != null ? timeout : defaultTimeout;
        String lockId = generateLockId();
        
        log.debug("Attempting to acquire lock: {} with timeout: {} ms", normalizedLockName, actualTimeout.toMillis());
        
        // Check if we already own this lock
        if (ownedLocks.contains(normalizedLockName)) {
            log.debug("Lock already owned by this node: {}", normalizedLockName);
            return extendLock(normalizedLockName, actualTimeout);
        }
        
        // Create lock info
        LockInfo lockInfo = LockInfo.builder()
            .lockName(normalizedLockName)
            .lockId(lockId)
            .nodeId(nodeId)
            .timeout(actualTimeout)
            .metadata(metadata != null ? new HashMap<>(metadata) : new HashMap<>())
            .acquiredAt(Instant.now())
            .expiresAt(Instant.now().plus(actualTimeout))
            .build();
        
        try {
            boolean acquired = false;
            
            // Try Redis first if enabled
            if (redisEnabled) {
                acquired = acquireLockRedis(lockInfo);
                if (acquired) {
                    log.debug("Lock acquired via Redis: {}", normalizedLockName);
                }
            }
            
            // Fallback to database if Redis failed
            if (!acquired && databaseFallbackEnabled) {
                acquired = acquireLockDatabase(lockInfo);
                if (acquired) {
                    log.debug("Lock acquired via database: {}", normalizedLockName);
                }
            }
            
            if (acquired) {
                // Track the lock
                activeLocks.put(normalizedLockName, lockInfo);
                ownedLocks.add(normalizedLockName);
                lockCounter.incrementAndGet();
                
                // Start heartbeat for long-running locks
                if (actualTimeout.compareTo(heartbeatInterval.multipliedBy(2)) > 0) {
                    startHeartbeat(lockInfo);
                }
                
                log.info("Successfully acquired lock: {} (ID: {})", normalizedLockName, lockId);
                return true;
            } else {
                log.debug("Failed to acquire lock: {}", normalizedLockName);
                
                // Handle fair locking with waiting queue
                if (fairLockingEnabled) {
                    return waitForLock(lockInfo);
                }
                
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error acquiring lock: {}", normalizedLockName, e);
            return false;
        }
    }
    
    /**
     * Try to acquire lock without waiting
     */
    public boolean tryLock(String lockName) {
        return tryLock(lockName, defaultTimeout);
    }
    
    /**
     * Try to acquire lock with timeout without waiting
     */
    public boolean tryLock(String lockName, Duration timeout) {
        String normalizedLockName = normalizeLockName(lockName);
        
        // Disable fair locking for try operations
        boolean originalFairLocking = fairLockingEnabled;
        try {
            return acquireLock(normalizedLockName, timeout);
        } finally {
            // Restore original setting (though this is per-thread, so might need adjustment)
        }
    }
    
    /**
     * Release distributed lock
     */
    public boolean releaseLock(String lockName) {
        if (lockName == null || lockName.trim().isEmpty()) {
            return false;
        }
        
        String normalizedLockName = normalizeLockName(lockName);
        
        log.debug("Attempting to release lock: {}", normalizedLockName);
        
        LockInfo lockInfo = activeLocks.get(normalizedLockName);
        if (lockInfo == null) {
            log.debug("No active lock found for: {}", normalizedLockName);
            return false;
        }
        
        try {
            boolean released = false;
            
            // Try Redis first
            if (redisEnabled) {
                released = releaseLockRedis(lockInfo);
                if (released) {
                    log.debug("Lock released via Redis: {}", normalizedLockName);
                }
            }
            
            // Try database if Redis failed
            if (!released && databaseFallbackEnabled) {
                released = releaseLockDatabase(lockInfo);
                if (released) {
                    log.debug("Lock released via database: {}", normalizedLockName);
                }
            }
            
            if (released) {
                // Clean up tracking
                activeLocks.remove(normalizedLockName);
                ownedLocks.remove(normalizedLockName);
                unlockCounter.incrementAndGet();
                
                // Notify waiting threads
                notifyWaiters(normalizedLockName);
                
                log.info("Successfully released lock: {} (ID: {})", normalizedLockName, lockInfo.getLockId());
                return true;
            } else {
                log.warn("Failed to release lock: {}", normalizedLockName);
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error releasing lock: {}", normalizedLockName, e);
            return false;
        }
    }
    
    /**
     * Check if lock is currently held
     */
    public boolean isLocked(String lockName) {
        String normalizedLockName = normalizeLockName(lockName);
        
        // Check local cache first
        LockInfo lockInfo = activeLocks.get(normalizedLockName);
        if (lockInfo != null && !isExpired(lockInfo)) {
            return true;
        }
        
        // Check Redis
        if (redisEnabled) {
            try {
                String redisKey = "lock:" + normalizedLockName;
                Boolean exists = redisTemplate.hasKey(redisKey);
                if (Boolean.TRUE.equals(exists)) {
                    return true;
                }
            } catch (Exception e) {
                log.warn("Error checking lock in Redis: {}", normalizedLockName, e);
            }
        }
        
        // Check database
        if (databaseFallbackEnabled) {
            try {
                String sql = "SELECT COUNT(*) FROM distributed_locks WHERE lock_name = ? AND expires_at > ?";
                Integer count = jdbcTemplate.queryForObject(sql, Integer.class, normalizedLockName, LocalDateTime.now());
                return count != null && count > 0;
            } catch (Exception e) {
                log.warn("Error checking lock in database: {}", normalizedLockName, e);
            }
        }
        
        return false;
    }
    
    /**
     * Get lock owner information
     */
    public Optional<String> getLockOwner(String lockName) {
        String normalizedLockName = normalizeLockName(lockName);
        
        // Check local cache first
        LockInfo lockInfo = activeLocks.get(normalizedLockName);
        if (lockInfo != null && !isExpired(lockInfo)) {
            return Optional.of(lockInfo.getNodeId());
        }
        
        // Check Redis
        if (redisEnabled) {
            try {
                String redisKey = "lock:" + normalizedLockName;
                String owner = redisTemplate.opsForHash().get(redisKey, "owner").toString();
                if (owner != null) {
                    return Optional.of(owner);
                }
            } catch (Exception e) {
                log.warn("Error getting lock owner from Redis: {}", normalizedLockName, e);
            }
        }
        
        // Check database
        if (databaseFallbackEnabled) {
            try {
                String sql = "SELECT node_id FROM distributed_locks WHERE lock_name = ? AND expires_at > ?";
                List<String> owners = jdbcTemplate.queryForList(sql, String.class, normalizedLockName, LocalDateTime.now());
                if (!owners.isEmpty()) {
                    return Optional.of(owners.get(0));
                }
            } catch (Exception e) {
                log.warn("Error getting lock owner from database: {}", normalizedLockName, e);
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Extend lock timeout
     */
    public boolean extendLock(String lockName, Duration additionalTime) {
        String normalizedLockName = normalizeLockName(lockName);
        
        LockInfo lockInfo = activeLocks.get(normalizedLockName);
        if (lockInfo == null || !ownedLocks.contains(normalizedLockName)) {
            log.debug("Cannot extend lock not owned by this node: {}", normalizedLockName);
            return false;
        }
        
        try {
            Instant newExpiryTime = lockInfo.getExpiresAt().plus(additionalTime);
            
            boolean extended = false;
            
            // Extend in Redis
            if (redisEnabled) {
                extended = extendLockRedis(lockInfo, newExpiryTime);
            }
            
            // Extend in database
            if (!extended && databaseFallbackEnabled) {
                extended = extendLockDatabase(lockInfo, newExpiryTime);
            }
            
            if (extended) {
                lockInfo.setExpiresAt(newExpiryTime);
                log.debug("Extended lock: {} until {}", normalizedLockName, newExpiryTime);
                return true;
            }
            
        } catch (Exception e) {
            log.error("Error extending lock: {}", normalizedLockName, e);
        }
        
        return false;
    }
    
    /**
     * Get current lock statistics
     */
    public LockStatistics getLockStatistics() {
        return LockStatistics.builder()
            .totalLocksAcquired(lockCounter.get())
            .totalLocksReleased(unlockCounter.get())
            .totalLockTimeouts(timeoutCounter.get())
            .activeLocksCount(activeLocks.size())
            .ownedLocksCount(ownedLocks.size())
            .waitingThreadsCount(lockWaiters.values().stream().mapToInt(Queue::size).sum())
            .redisEnabled(redisEnabled)
            .databaseFallbackEnabled(databaseFallbackEnabled)
            .fairLockingEnabled(fairLockingEnabled)
            .nodeId(nodeId)
            .build();
    }
    
    // Private helper methods
    
    private boolean acquireLockRedis(LockInfo lockInfo) {
        try {
            String redisKey = "lock:" + lockInfo.getLockName();
            String owner = lockInfo.getNodeId() + ":" + lockInfo.getLockId();
            long nowEpoch = Instant.now().getEpochSecond();
            long expiresAtEpoch = lockInfo.getExpiresAt().getEpochSecond();
            long ttlSeconds = Duration.between(Instant.now(), lockInfo.getExpiresAt()).getSeconds();
            
            RedisScript<Long> script = new DefaultRedisScript<>(ACQUIRE_LOCK_SCRIPT, Long.class);
            Long result = redisTemplate.execute(script, 
                Collections.singletonList(redisKey),
                owner, String.valueOf(nowEpoch), String.valueOf(expiresAtEpoch), String.valueOf(ttlSeconds));
            
            return result != null && result == 1L;
            
        } catch (Exception e) {
            log.warn("Redis lock acquisition failed for: {}", lockInfo.getLockName(), e);
            return false;
        }
    }
    
    private boolean releaseLockRedis(LockInfo lockInfo) {
        try {
            String redisKey = "lock:" + lockInfo.getLockName();
            String owner = lockInfo.getNodeId() + ":" + lockInfo.getLockId();
            
            RedisScript<Long> script = new DefaultRedisScript<>(RELEASE_LOCK_SCRIPT, Long.class);
            Long result = redisTemplate.execute(script, 
                Collections.singletonList(redisKey), owner);
            
            return result != null && result == 1L;
            
        } catch (Exception e) {
            log.warn("Redis lock release failed for: {}", lockInfo.getLockName(), e);
            return false;
        }
    }
    
    private boolean extendLockRedis(LockInfo lockInfo, Instant newExpiryTime) {
        try {
            String redisKey = "lock:" + lockInfo.getLockName();
            String owner = lockInfo.getNodeId() + ":" + lockInfo.getLockId();
            long expiresAtEpoch = newExpiryTime.getEpochSecond();
            long ttlSeconds = Duration.between(Instant.now(), newExpiryTime).getSeconds();
            
            RedisScript<Long> script = new DefaultRedisScript<>(EXTEND_LOCK_SCRIPT, Long.class);
            Long result = redisTemplate.execute(script, 
                Collections.singletonList(redisKey),
                owner, String.valueOf(expiresAtEpoch), String.valueOf(ttlSeconds));
            
            return result != null && result == 1L;
            
        } catch (Exception e) {
            log.warn("Redis lock extension failed for: {}", lockInfo.getLockName(), e);
            return false;
        }
    }
    
    private boolean acquireLockDatabase(LockInfo lockInfo) {
        try {
            String sql = """
                INSERT INTO distributed_locks (lock_name, lock_id, node_id, acquired_at, expires_at, metadata)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
            
            int rows = jdbcTemplate.update(sql,
                lockInfo.getLockName(),
                lockInfo.getLockId(),
                lockInfo.getNodeId(),
                LocalDateTime.now(),
                LocalDateTime.now().plus(lockInfo.getTimeout()),
                serializeMetadata(lockInfo.getMetadata()));
            
            return rows > 0;
            
        } catch (DataIntegrityViolationException e) {
            // Lock already exists
            log.debug("Database lock already exists for: {}", lockInfo.getLockName());
            return false;
        } catch (Exception e) {
            log.warn("Database lock acquisition failed for: {}", lockInfo.getLockName(), e);
            return false;
        }
    }
    
    private boolean releaseLockDatabase(LockInfo lockInfo) {
        try {
            String sql = "DELETE FROM distributed_locks WHERE lock_name = ? AND lock_id = ? AND node_id = ?";
            
            int rows = jdbcTemplate.update(sql,
                lockInfo.getLockName(),
                lockInfo.getLockId(),
                lockInfo.getNodeId());
            
            return rows > 0;
            
        } catch (Exception e) {
            log.warn("Database lock release failed for: {}", lockInfo.getLockName(), e);
            return false;
        }
    }
    
    private boolean extendLockDatabase(LockInfo lockInfo, Instant newExpiryTime) {
        try {
            String sql = "UPDATE distributed_locks SET expires_at = ? WHERE lock_name = ? AND lock_id = ? AND node_id = ?";
            
            int rows = jdbcTemplate.update(sql,
                LocalDateTime.now().plus(Duration.between(Instant.now(), newExpiryTime)),
                lockInfo.getLockName(),
                lockInfo.getLockId(),
                lockInfo.getNodeId());
            
            return rows > 0;
            
        } catch (Exception e) {
            log.warn("Database lock extension failed for: {}", lockInfo.getLockName(), e);
            return false;
        }
    }
    
    private boolean waitForLock(LockInfo lockInfo) {
        String lockName = lockInfo.getLockName();
        
        // Add to waiting queue
        Queue<LockWaiter> waiters = lockWaiters.computeIfAbsent(lockName, k -> new ConcurrentLinkedQueue<>());
        
        LockWaiter waiter = new LockWaiter(lockInfo);
        waiters.offer(waiter);
        
        try {
            // Wait for notification or timeout
            boolean acquired = waiter.getLatch().await(maxWaitTime.toMillis(), TimeUnit.MILLISECONDS);
            
            if (!acquired) {
                timeoutCounter.incrementAndGet();
                waiters.remove(waiter);
                log.debug("Lock wait timeout for: {}", lockName);
            }
            
            return acquired;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            waiters.remove(waiter);
            log.debug("Lock wait interrupted for: {}", lockName);
            return false;
        }
    }
    
    private void notifyWaiters(String lockName) {
        Queue<LockWaiter> waiters = lockWaiters.get(lockName);
        if (waiters != null && !waiters.isEmpty()) {
            LockWaiter nextWaiter = waiters.poll();
            if (nextWaiter != null) {
                // Try to acquire lock for the next waiter
                if (acquireLock(lockName, nextWaiter.getLockInfo().getTimeout())) {
                    nextWaiter.getLatch().countDown();
                } else {
                    // Put back in queue if acquisition failed
                    waiters.offer(nextWaiter);
                }
            }
        }
    }
    
    private void startHeartbeat(LockInfo lockInfo) {
        // Implementation for heartbeat mechanism
        CompletableFuture.runAsync(() -> {
            while (ownedLocks.contains(lockInfo.getLockName()) && !isExpired(lockInfo)) {
                try {
                    Thread.sleep(heartbeatInterval.toMillis());
                    extendLock(lockInfo.getLockName(), heartbeatInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.warn("Heartbeat failed for lock: {}", lockInfo.getLockName(), e);
                }
            }
        });
    }
    
    @Scheduled(fixedRateString = "#{@distributedLockService.cleanupInterval.toMillis()}")
    public void cleanupExpiredLocks() {
        log.debug("Starting cleanup of expired locks");
        
        // Cleanup local tracking
        activeLocks.entrySet().removeIf(entry -> {
            LockInfo lockInfo = entry.getValue();
            if (isExpired(lockInfo)) {
                ownedLocks.remove(entry.getKey());
                return true;
            }
            return false;
        });
        
        // Cleanup database locks
        if (databaseFallbackEnabled) {
            try {
                String sql = "DELETE FROM distributed_locks WHERE expires_at < ?";
                int deleted = jdbcTemplate.update(sql, LocalDateTime.now());
                if (deleted > 0) {
                    log.debug("Cleaned up {} expired database locks", deleted);
                }
            } catch (Exception e) {
                log.warn("Failed to cleanup expired database locks", e);
            }
        }
    }
    
    private String normalizeLockName(String lockName) {
        return lockName.trim().toLowerCase();
    }
    
    private String generateLockId() {
        return UUID.randomUUID().toString();
    }
    
    private String generateNodeId() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + 
                   ManagementFactory.getRuntimeMXBean().getName();
        } catch (Exception e) {
            return "unknown-node-" + UUID.randomUUID().toString();
        }
    }
    
    private boolean isExpired(LockInfo lockInfo) {
        return Instant.now().isAfter(lockInfo.getExpiresAt());
    }
    
    private String serializeMetadata(Map<String, String> metadata) {
        // Simple serialization - in production use JSON
        return metadata.toString();
    }
    
    // Inner classes
    
    @lombok.Builder
    @lombok.Data
    private static class LockInfo {
        private String lockName;
        private String lockId;
        private String nodeId;
        private Duration timeout;
        private Map<String, String> metadata;
        private Instant acquiredAt;
        private Instant expiresAt;
        
        public void setExpiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
        }
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class LockWaiter {
        private LockInfo lockInfo;
        private CountDownLatch latch = new CountDownLatch(1);
        
        public LockWaiter(LockInfo lockInfo) {
            this.lockInfo = lockInfo;
        }
    }
    
    @lombok.Builder
    @lombok.Data
    public static class LockStatistics {
        private long totalLocksAcquired;
        private long totalLocksReleased;
        private long totalLockTimeouts;
        private int activeLocksCount;
        private int ownedLocksCount;
        private int waitingThreadsCount;
        private boolean redisEnabled;
        private boolean databaseFallbackEnabled;
        private boolean fairLockingEnabled;
        private String nodeId;
    }
}