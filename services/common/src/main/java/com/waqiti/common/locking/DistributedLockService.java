package com.waqiti.common.locking;

import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Distributed locking service using Redis for financial operations.
 * Implements Redis-based distributed locks with automatic expiration.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedLockService {

    private final RedisTemplate<String, String> redisTemplate;
    
    private static final String LOCK_PREFIX = "lock:";
    private static final String UNLOCK_SCRIPT = 
        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
        "    return redis.call('del', KEYS[1]) " +
        "else " +
        "    return 0 " +
        "end";
    
    private final DefaultRedisScript<Long> unlockScript;
    
    public DistributedLockService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.unlockScript = new DefaultRedisScript<>();
        this.unlockScript.setScriptText(UNLOCK_SCRIPT);
        this.unlockScript.setResultType(Long.class);
    }

    /**
     * Acquires a distributed lock for financial operations.
     * 
     * @param lockKey The unique key for the lock
     * @param timeout Maximum time to wait for the lock
     * @param leaseTime Maximum time to hold the lock
     * @return DistributedLock instance if successful, null if failed
     */
    @Timed("distributed.lock.acquire")
    public DistributedLock acquireLock(String lockKey, Duration timeout, Duration leaseTime) {
        String redisKey = LOCK_PREFIX + lockKey;
        String lockValue = generateLockValue();
        
        Instant deadline = Instant.now().plus(timeout);
        
        while (Instant.now().isBefore(deadline)) {
            Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, lockValue, leaseTime);
            
            if (Boolean.TRUE.equals(acquired)) {
                log.debug("Lock acquired successfully: {}", lockKey);
                return new DistributedLock(redisKey, lockValue, leaseTime, this);
            }
            
            try {
                Thread.sleep(50); // Small delay before retry
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("CRITICAL: Lock acquisition interrupted for financial operation: {}", lockKey);
                throw new DistributedLockException("Lock acquisition interrupted for financial operation: " + lockKey, e);
            }
        }
        
        log.error("CRITICAL: Failed to acquire distributed lock within timeout for financial operation: {}", lockKey);
        throw new DistributedLockException("Failed to acquire distributed lock within timeout: " + lockKey + ". This could cause financial transaction consistency issues.");
    }

    /**
     * Acquires a lock with default timeout (5 seconds) and lease time (30 seconds).
     */
    public DistributedLock acquireLock(String lockKey) {
        return acquireLock(lockKey, Duration.ofSeconds(5), Duration.ofSeconds(30));
    }

    /**
     * Alias for acquireLock - acquires a distributed lock.
     * @param lockKey The unique key for the lock
     * @param timeout Maximum time to wait for the lock
     * @param leaseTime Maximum time to hold the lock
     * @return DistributedLock instance if successful, null if failed
     */
    public DistributedLock acquire(String lockKey, Duration timeout, Duration leaseTime) {
        return acquireLock(lockKey, timeout, leaseTime);
    }

    /**
     * Releases a distributed lock.
     * 
     * @param redisKey The Redis key for the lock
     * @param lockValue The value associated with the lock
     * @return true if successfully released, false otherwise
     */
    @Timed("distributed.lock.release")
    public boolean releaseLock(String redisKey, String lockValue) {
        try {
            Long result = redisTemplate.execute(unlockScript, 
                Collections.singletonList(redisKey), lockValue);
            
            boolean released = result != null && result == 1L;
            if (released) {
                log.debug("Lock released successfully: {}", redisKey);
            } else {
                log.warn("Lock release failed - may have expired: {}", redisKey);
            }
            
            return released;
            
        } catch (Exception e) {
            log.error("Error releasing lock: {}", redisKey, e);
            return false;
        }
    }

    /**
     * Attempts to extend the lease time of an existing lock.
     * 
     * @param redisKey The Redis key for the lock
     * @param lockValue The value associated with the lock
     * @param additionalTime Additional time to extend the lease
     * @return true if successfully extended, false otherwise
     */
    @Timed("distributed.lock.extend")
    public boolean extendLock(String redisKey, String lockValue, Duration additionalTime) {
        try {
            String currentValue = redisTemplate.opsForValue().get(redisKey);
            
            if (lockValue.equals(currentValue)) {
                Boolean extended = redisTemplate.expire(redisKey, additionalTime.toSeconds(), TimeUnit.SECONDS);
                
                if (Boolean.TRUE.equals(extended)) {
                    log.debug("Lock extended successfully: {}", redisKey);
                    return true;
                }
            }
            
            log.warn("Lock extension failed: {}", redisKey);
            return false;
            
        } catch (Exception e) {
            log.error("Error extending lock: {}", redisKey, e);
            return false;
        }
    }

    /**
     * Checks if a lock is currently held.
     * 
     * @param lockKey The lock key to check
     * @return true if lock exists, false otherwise
     */
    public boolean isLocked(String lockKey) {
        String redisKey = LOCK_PREFIX + lockKey;
        return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
    }

    /**
     * Gets the remaining time to live for a lock.
     * 
     * @param lockKey The lock key to check
     * @return Duration remaining, or null if lock doesn't exist
     */
    public Duration getLockTTL(String lockKey) {
        String redisKey = LOCK_PREFIX + lockKey;
        Long ttlSeconds = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
        
        if (ttlSeconds != null && ttlSeconds > 0) {
            return Duration.ofSeconds(ttlSeconds);
        }
        
        log.warn("Lock TTL not available or expired for: {}", lockKey);
        return Duration.ZERO; // Return zero duration instead of null
    }

    /**
     * Creates financial operation locks with standardized naming.
     */
    public static class FinancialLocks {
        
        public static String walletBalanceUpdate(UUID walletId) {
            return "wallet:balance:" + walletId;
        }
        
        public static String userAccountUpdate(UUID userId) {
            return "user:account:" + userId;
        }
        
        public static String paymentProcessing(UUID paymentId) {
            return "payment:processing:" + paymentId;
        }
        
        public static String transferOperation(UUID fromWalletId, UUID toWalletId) {
            // Ensure consistent ordering to prevent deadlocks
            UUID first = fromWalletId.compareTo(toWalletId) < 0 ? fromWalletId : toWalletId;
            UUID second = fromWalletId.compareTo(toWalletId) < 0 ? toWalletId : fromWalletId;
            return "transfer:" + first + ":" + second;
        }
        
        public static String ledgerEntry(String accountId) {
            return "ledger:account:" + accountId;
        }
        
        public static String reconciliation(String reconciliationType, String date) {
            return "reconciliation:" + reconciliationType + ":" + date;
        }
    }

    private String generateLockValue() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * Exception thrown when distributed lock operations fail
     */
    public static class DistributedLockException extends RuntimeException {
        public DistributedLockException(String message) {
            super(message);
        }
        
        public DistributedLockException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}