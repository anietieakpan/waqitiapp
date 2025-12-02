package com.waqiti.common.locking;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;

/**
 * Represents a distributed lock that can be used in try-with-resources blocks.
 * Automatically releases the lock when closed.
 */
@Slf4j
public class DistributedLock implements AutoCloseable {

    @Getter
    private final String redisKey;
    
    @Getter
    private final String lockValue;
    
    @Getter
    private final Duration leaseTime;
    
    private final DistributedLockService lockService;
    
    @Getter
    private final Instant acquiredAt;
    
    private volatile boolean released = false;

    public DistributedLock(String redisKey, String lockValue, Duration leaseTime, 
                          DistributedLockService lockService) {
        this.redisKey = redisKey;
        this.lockValue = lockValue;
        this.leaseTime = leaseTime;
        this.lockService = lockService;
        this.acquiredAt = Instant.now();
    }

    /**
     * Extends the lock lease time.
     * 
     * @param additionalTime Additional time to extend the lease
     * @return true if successfully extended, false otherwise
     */
    public boolean extend(Duration additionalTime) {
        if (released) {
            log.warn("Cannot extend released lock: {}", redisKey);
            return false;
        }
        
        return lockService.extendLock(redisKey, lockValue, additionalTime);
    }

    /**
     * Checks if the lock is still valid (not expired or released).
     * 
     * @return true if lock is still valid, false otherwise
     */
    public boolean isValid() {
        if (released) {
            return false;
        }
        
        Instant expiresAt = acquiredAt.plus(leaseTime);
        return Instant.now().isBefore(expiresAt);
    }

    /**
     * Gets the remaining time before the lock expires.
     * 
     * @return Duration remaining, or null if expired/released
     */
    public Duration getTimeRemaining() {
        if (released) {
            return Duration.ZERO;
        }
        
        Instant expiresAt = acquiredAt.plus(leaseTime);
        Instant now = Instant.now();
        
        if (now.isBefore(expiresAt)) {
            return Duration.between(now, expiresAt);
        }
        
        return Duration.ZERO;
    }

    /**
     * Manually releases the lock.
     * 
     * @return true if successfully released, false otherwise
     */
    public boolean release() {
        if (released) {
            log.debug("Lock already released: {}", redisKey);
            return true;
        }
        
        boolean success = lockService.releaseLock(redisKey, lockValue);
        if (success) {
            released = true;
        }
        
        return success;
    }

    @Override
    public void close() {
        if (!released) {
            release();
        }
    }

    @Override
    public String toString() {
        return String.format("DistributedLock{key='%s', acquired=%s, leaseTime=%s, released=%s}", 
                redisKey, acquiredAt, leaseTime, released);
    }
}