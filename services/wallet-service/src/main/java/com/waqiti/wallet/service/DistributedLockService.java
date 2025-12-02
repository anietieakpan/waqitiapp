package com.waqiti.wallet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Distributed Lock Service using Redisson
 * 
 * Provides distributed locking capabilities for:
 * - Wallet balance updates
 * - Transaction processing
 * - Critical section protection
 * - Preventing race conditions
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedLockService {
    
    private final RedissonClient redissonClient;
    
    @Value("${distributed.lock.wait-time:30}")
    private long defaultWaitTime;
    
    @Value("${distributed.lock.lease-time:10}")
    private long defaultLeaseTime;
    
    private static final String LOCK_PREFIX = "waqiti:lock:";
    
    /**
     * Execute an operation with a distributed lock
     * 
     * @param lockKey The key for the lock
     * @param operation The operation to execute
     * @return The result of the operation
     * @throws RuntimeException if lock cannot be acquired or operation fails
     */
    public <T> T executeWithLock(String lockKey, Callable<T> operation) {
        return executeWithLock(lockKey, Duration.ofSeconds(defaultWaitTime), 
                              Duration.ofSeconds(defaultLeaseTime), operation);
    }
    
    /**
     * Execute an operation with a distributed lock with custom timeout
     * 
     * @param lockKey The key for the lock
     * @param waitTime Maximum time to wait for lock
     * @param operation The operation to execute
     * @return The result of the operation
     */
    public <T> T executeWithLock(String lockKey, Duration waitTime, Callable<T> operation) {
        return executeWithLock(lockKey, waitTime, Duration.ofSeconds(defaultLeaseTime), operation);
    }
    
    /**
     * Execute an operation with a distributed lock with full control
     * 
     * @param lockKey The key for the lock
     * @param waitTime Maximum time to wait for lock
     * @param leaseTime Time after which lock will be automatically released
     * @param operation The operation to execute
     * @return The result of the operation
     */
    public <T> T executeWithLock(String lockKey, Duration waitTime, Duration leaseTime, 
                                 Callable<T> operation) {
        String fullLockKey = LOCK_PREFIX + lockKey;
        RLock lock = redissonClient.getLock(fullLockKey);
        
        log.debug("Attempting to acquire lock: {}", fullLockKey);
        
        try {
            boolean acquired = lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), 
                                           TimeUnit.MILLISECONDS);
            
            if (!acquired) {
                log.error("Failed to acquire lock: {} after {} ms", fullLockKey, waitTime.toMillis());
                throw new LockAcquisitionException("Could not acquire lock: " + lockKey);
            }
            
            log.debug("Lock acquired: {}", fullLockKey);
            
            try {
                return operation.call();
            } finally {
                // Ensure we only unlock if we hold the lock
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                    log.debug("Lock released: {}", fullLockKey);
                }
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException("Interrupted while acquiring lock: " + lockKey, e);
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Operation failed under lock: " + lockKey, e);
        }
    }
    
    /**
     * Execute an operation with a distributed lock, with fallback on failure
     */
    public <T> T executeWithLockOrFallback(String lockKey, Callable<T> operation, 
                                          Callable<T> fallback) {
        try {
            return executeWithLock(lockKey, operation);
        } catch (LockAcquisitionException e) {
            log.warn("Lock acquisition failed, executing fallback for: {}", lockKey);
            try {
                return fallback.call();
            } catch (Exception fe) {
                throw new RuntimeException("Fallback operation failed", fe);
            }
        }
    }
    
    /**
     * Try to execute with lock, return empty if lock cannot be acquired
     */
    public <T> Optional<T> tryExecuteWithLock(String lockKey, Duration waitTime, 
                                             Callable<T> operation) {
        try {
            return Optional.of(executeWithLock(lockKey, waitTime, operation));
        } catch (LockAcquisitionException e) {
            log.debug("Could not acquire lock: {}, returning empty", lockKey);
            return Optional.empty();
        }
    }
    
    /**
     * Execute void operation with lock
     */
    public void executeWithLock(String lockKey, Runnable operation) {
        executeWithLock(lockKey, () -> {
            operation.run();
            return null;
        });
    }
    
    /**
     * Check if a lock is currently held
     */
    public boolean isLocked(String lockKey) {
        String fullLockKey = LOCK_PREFIX + lockKey;
        RLock lock = redissonClient.getLock(fullLockKey);
        return lock.isLocked();
    }
    
    /**
     * Force unlock a lock (use with caution)
     */
    public void forceUnlock(String lockKey) {
        String fullLockKey = LOCK_PREFIX + lockKey;
        RLock lock = redissonClient.getLock(fullLockKey);
        if (lock.isLocked()) {
            lock.forceUnlock();
            log.warn("Force unlocked: {}", fullLockKey);
        }
    }
    
    /**
     * Get remaining lease time for a lock
     */
    public long getRemainingLeaseTime(String lockKey) {
        String fullLockKey = LOCK_PREFIX + lockKey;
        RLock lock = redissonClient.getLock(fullLockKey);
        return lock.remainTimeToLive();
    }
    
    /**
     * Exception thrown when lock cannot be acquired
     */
    public static class LockAcquisitionException extends RuntimeException {
        public LockAcquisitionException(String message) {
            super(message);
        }
        
        public LockAcquisitionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}