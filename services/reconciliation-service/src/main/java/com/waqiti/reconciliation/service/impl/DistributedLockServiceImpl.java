package com.waqiti.reconciliation.service.impl;

import com.waqiti.reconciliation.service.DistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * DistributedLockServiceImpl - Redis-based distributed locking implementation
 *
 * Provides production-grade distributed locking using Redis with:
 * - Automatic lock expiration to prevent deadlocks
 * - Lock renewal for long-running operations
 * - Proper cleanup on failure
 * - Thread-safe lock tracking
 * - Detailed logging for troubleshooting
 *
 * Implementation uses Redis SET with NX (not exists) and PX (expiration) options
 * for atomic lock acquisition.
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockServiceImpl implements DistributedLockService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String LOCK_PREFIX = "reconciliation:lock:";
    private static final long DEFAULT_WAIT_TIME_SECONDS = 10;
    private static final long DEFAULT_LEASE_TIME_SECONDS = 30;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 100;

    // Track locks held by this instance
    private final Map<String, String> heldLocks = new ConcurrentHashMap<>();

    /**
     * Execute a task with distributed lock using default timeouts
     */
    @Override
    public <T> T executeWithLock(String lockKey, Callable<T> task) throws Exception {
        return executeWithLock(
            lockKey,
            task,
            DEFAULT_WAIT_TIME_SECONDS,
            DEFAULT_LEASE_TIME_SECONDS,
            TimeUnit.SECONDS
        );
    }

    /**
     * Execute a task with distributed lock with custom timeouts
     */
    @Override
    public <T> T executeWithLock(String lockKey, Callable<T> task,
                                 long waitTime, long leaseTime, TimeUnit timeUnit) throws Exception {
        String fullLockKey = LOCK_PREFIX + lockKey;
        String lockValue = generateLockValue();

        log.debug("Attempting to acquire lock: {} with wait time: {} {}, lease time: {} {}",
            lockKey, waitTime, timeUnit, leaseTime, timeUnit);

        boolean acquired = false;
        int attempts = 0;

        // Try to acquire lock with retries
        while (!acquired && attempts < MAX_RETRY_ATTEMPTS) {
            acquired = acquireLock(fullLockKey, lockValue, leaseTime, timeUnit);

            if (!acquired) {
                attempts++;
                if (attempts < MAX_RETRY_ATTEMPTS) {
                    log.debug("Lock acquisition attempt {} failed for key: {}, retrying...",
                        attempts, lockKey);
                    Thread.sleep(RETRY_DELAY_MS * attempts);
                }
            }
        }

        if (!acquired) {
            log.warn("Failed to acquire lock after {} attempts: {}", MAX_RETRY_ATTEMPTS, lockKey);
            throw new IllegalStateException("Could not acquire lock: " + lockKey);
        }

        // Track the lock
        heldLocks.put(fullLockKey, lockValue);

        log.info("Lock acquired successfully: {}", lockKey);

        try {
            // Execute the task
            T result = task.call();
            log.debug("Task completed successfully for lock: {}", lockKey);
            return result;

        } catch (Exception e) {
            log.error("Task execution failed for lock: {}", lockKey, e);
            throw e;

        } finally {
            // Always release the lock
            releaseLock(fullLockKey, lockValue);
            heldLocks.remove(fullLockKey);
            log.debug("Lock released: {}", lockKey);
        }
    }

    /**
     * Try to acquire lock without blocking
     */
    @Override
    public boolean tryLock(String lockKey) {
        return tryLock(lockKey, 0, TimeUnit.SECONDS);
    }

    /**
     * Try to acquire lock with timeout
     */
    @Override
    public boolean tryLock(String lockKey, long waitTime, TimeUnit timeUnit) {
        String fullLockKey = LOCK_PREFIX + lockKey;
        String lockValue = generateLockValue();

        boolean acquired = acquireLock(fullLockKey, lockValue, DEFAULT_LEASE_TIME_SECONDS, TimeUnit.SECONDS);

        if (acquired) {
            heldLocks.put(fullLockKey, lockValue);
            log.debug("Lock acquired: {}", lockKey);
        } else {
            log.debug("Failed to acquire lock: {}", lockKey);
        }

        return acquired;
    }

    /**
     * Release a lock
     */
    @Override
    public void unlock(String lockKey) {
        String fullLockKey = LOCK_PREFIX + lockKey;
        String lockValue = heldLocks.get(fullLockKey);

        if (lockValue != null) {
            releaseLock(fullLockKey, lockValue);
            heldLocks.remove(fullLockKey);
            log.debug("Lock released manually: {}", lockKey);
        } else {
            log.warn("Attempted to release lock not held by this instance: {}", lockKey);
        }
    }

    /**
     * Check if lock is currently held
     */
    @Override
    public boolean isLocked(String lockKey) {
        String fullLockKey = LOCK_PREFIX + lockKey;

        try {
            Boolean hasKey = redisTemplate.hasKey(fullLockKey);
            return Boolean.TRUE.equals(hasKey);
        } catch (Exception e) {
            log.error("Error checking lock status for key: {}", lockKey, e);
            return false;
        }
    }

    // Private helper methods

    /**
     * Acquire lock using Redis SET with NX and PX options
     */
    private boolean acquireLock(String lockKey, String lockValue, long leaseTime, TimeUnit timeUnit) {
        try {
            Duration duration = Duration.ofMillis(timeUnit.toMillis(leaseTime));

            // Use SET NX PX for atomic lock acquisition with expiration
            Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, duration);

            return Boolean.TRUE.equals(result);

        } catch (Exception e) {
            log.error("Error acquiring lock for key: {}", lockKey, e);
            return false;
        }
    }

    /**
     * Release lock using Lua script for atomic check-and-delete
     * Only releases if the lock is held by this instance
     */
    private void releaseLock(String lockKey, String lockValue) {
        try {
            // Lua script for atomic check and delete
            String luaScript =
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "    return redis.call('del', KEYS[1]) " +
                "else " +
                "    return 0 " +
                "end";

            Long result = redisTemplate.execute(
                org.springframework.data.redis.core.script.RedisScript.of(luaScript, Long.class),
                java.util.Collections.singletonList(lockKey),
                lockValue
            );

            if (result != null && result == 1) {
                log.trace("Lock released successfully: {}", lockKey);
            } else {
                log.warn("Lock was not released (may have expired or been stolen): {}", lockKey);
            }

        } catch (Exception e) {
            log.error("Error releasing lock for key: {}", lockKey, e);
        }
    }

    /**
     * Generate unique lock value to identify lock owner
     */
    private String generateLockValue() {
        return UUID.randomUUID().toString() + ":" + Thread.currentThread().getId();
    }

    /**
     * Extend lock lease time for long-running operations
     */
    public boolean extendLock(String lockKey, long additionalTime, TimeUnit timeUnit) {
        String fullLockKey = LOCK_PREFIX + lockKey;
        String lockValue = heldLocks.get(fullLockKey);

        if (lockValue == null) {
            log.warn("Cannot extend lock not held by this instance: {}", lockKey);
            return false;
        }

        try {
            // Check current value matches
            String currentValue = redisTemplate.opsForValue().get(fullLockKey);

            if (lockValue.equals(currentValue)) {
                Duration duration = Duration.ofMillis(timeUnit.toMillis(additionalTime));
                Boolean result = redisTemplate.expire(fullLockKey, duration);

                if (Boolean.TRUE.equals(result)) {
                    log.debug("Lock lease extended for key: {}", lockKey);
                    return true;
                }
            }

            log.warn("Failed to extend lock (value mismatch): {}", lockKey);
            return false;

        } catch (Exception e) {
            log.error("Error extending lock for key: {}", lockKey, e);
            return false;
        }
    }

    /**
     * Force release a lock (use with caution - only for cleanup)
     */
    public void forceUnlock(String lockKey) {
        String fullLockKey = LOCK_PREFIX + lockKey;

        try {
            Boolean result = redisTemplate.delete(fullLockKey);

            if (Boolean.TRUE.equals(result)) {
                log.warn("Lock forcefully released: {}", lockKey);
                heldLocks.remove(fullLockKey);
            }

        } catch (Exception e) {
            log.error("Error force releasing lock for key: {}", lockKey, e);
        }
    }

    /**
     * Get information about a lock
     */
    public LockInfo getLockInfo(String lockKey) {
        String fullLockKey = LOCK_PREFIX + lockKey;

        try {
            String value = redisTemplate.opsForValue().get(fullLockKey);
            Long ttl = redisTemplate.getExpire(fullLockKey, TimeUnit.SECONDS);

            if (value != null) {
                boolean heldByThis = value.equals(heldLocks.get(fullLockKey));

                return LockInfo.builder()
                    .lockKey(lockKey)
                    .locked(true)
                    .heldByThisInstance(heldByThis)
                    .ttlSeconds(ttl != null ? ttl : -1)
                    .build();
            }

            return LockInfo.builder()
                .lockKey(lockKey)
                .locked(false)
                .heldByThisInstance(false)
                .ttlSeconds(-1)
                .build();

        } catch (Exception e) {
            log.error("Error getting lock info for key: {}", lockKey, e);
            return LockInfo.builder()
                .lockKey(lockKey)
                .locked(false)
                .heldByThisInstance(false)
                .ttlSeconds(-1)
                .error(e.getMessage())
                .build();
        }
    }

    /**
     * Clean up expired locks held by this instance
     */
    public void cleanupExpiredLocks() {
        log.debug("Cleaning up expired locks, current tracked: {}", heldLocks.size());

        heldLocks.entrySet().removeIf(entry -> {
            String lockKey = entry.getKey();
            String lockValue = entry.getValue();

            try {
                String currentValue = redisTemplate.opsForValue().get(lockKey);

                // Remove from tracking if lock no longer exists or value changed
                if (currentValue == null || !currentValue.equals(lockValue)) {
                    log.debug("Removing stale lock from tracking: {}", lockKey);
                    return true;
                }

                return false;

            } catch (Exception e) {
                log.error("Error checking lock during cleanup: {}", lockKey, e);
                return false;
            }
        });

        log.debug("Cleanup complete, remaining tracked locks: {}", heldLocks.size());
    }

    // Inner classes

    @lombok.Builder
    @lombok.Data
    public static class LockInfo {
        private String lockKey;
        private boolean locked;
        private boolean heldByThisInstance;
        private long ttlSeconds;
        private String error;
    }
}
