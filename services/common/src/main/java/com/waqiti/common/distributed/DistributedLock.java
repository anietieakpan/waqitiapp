package com.waqiti.common.distributed;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents a distributed lock token with automatic release on close.
 * Implements AutoCloseable for use with try-with-resources pattern.
 * 
 * @author Waqiti Security Team
 * @version 2.0.0
 * @since 2025-09-16
 */
@Slf4j
@Getter
public class DistributedLock implements AutoCloseable {
    
    private final String lockName;
    private final String lockValue;
    private final int leaseTimeSeconds;
    private final Instant acquiredAt;
    private final AtomicBoolean released;
    
    // For tracking and monitoring
    private final String threadName;
    private final StackTraceElement[] acquiredStackTrace;
    
    public DistributedLock(String lockName, String lockValue, int leaseTimeSeconds) {
        this.lockName = lockName;
        this.lockValue = lockValue;
        this.leaseTimeSeconds = leaseTimeSeconds;
        this.acquiredAt = Instant.now();
        this.released = new AtomicBoolean(false);
        this.threadName = Thread.currentThread().getName();
        this.acquiredStackTrace = Thread.currentThread().getStackTrace();
    }
    
    /**
     * Marks the lock as released.
     */
    public void markReleased() {
        if (released.compareAndSet(false, true)) {
            log.trace("Lock marked as released: {}", lockName);
        }
    }
    
    /**
     * Checks if the lock has been released.
     */
    public boolean isReleased() {
        return released.get();
    }
    
    /**
     * Checks if the lock is expired based on lease time.
     */
    public boolean isExpired() {
        if (leaseTimeSeconds <= 0) {
            return false; // Degraded mode lock never expires
        }
        return Instant.now().isAfter(acquiredAt.plusSeconds(leaseTimeSeconds));
    }
    
    /**
     * Gets remaining lease time in seconds.
     */
    public long getRemainingLeaseSeconds() {
        if (leaseTimeSeconds <= 0) {
            return Long.MAX_VALUE; // Degraded mode
        }
        long elapsed = Instant.now().getEpochSecond() - acquiredAt.getEpochSecond();
        return Math.max(0, leaseTimeSeconds - elapsed);
    }
    
    /**
     * Checks if this is a degraded mode lock (Redis unavailable).
     */
    public boolean isDegradedMode() {
        return lockValue.startsWith("DEGRADED-");
    }
    
    /**
     * Attempts to acquire the lock with specified wait and lease times.
     * 
     * @param waitTime Maximum time to wait for lock acquisition
     * @param unit Time unit for wait time
     * @return true if lock acquired successfully (already acquired in this case)
     */
    public boolean tryLock(long waitTime, TimeUnit unit) {
        // This represents an already acquired lock, so return true if not released
        return !isReleased();
    }
    
    /**
     * Releases this lock instance.
     */
    public void unlock() {
        markReleased();
    }
    
    /**
     * Checks if this lock is currently held (not released).
     * 
     * @return true if lock is still held
     */
    public boolean isLocked() {
        return !isReleased();
    }
    
    /**
     * Gets the unique lock key/name.
     * 
     * @return lock name
     */
    public String getLockKey() {
        return lockName;
    }
    
    /**
     * Gets the time when this lock was acquired as LocalDateTime.
     * 
     * @return acquisition timestamp as LocalDateTime
     */
    public LocalDateTime getAcquisitionTime() {
        return LocalDateTime.ofInstant(acquiredAt, java.time.ZoneId.systemDefault());
    }
    
    /**
     * Gets the time this lock has been held in milliseconds.
     * 
     * @return hold time in milliseconds
     */
    public long getHoldTimeMs() {
        return Instant.now().toEpochMilli() - acquiredAt.toEpochMilli();
    }
    
    /**
     * AutoCloseable implementation for automatic lock release.
     * Allows usage with try-with-resources pattern.
     */
    @Override
    public void close() {
        if (!isReleased() && !isDegradedMode()) {
            log.warn("Lock {} was not explicitly released, auto-releasing from close()", lockName);
            markReleased();
            // Note: Actual Redis release should be handled by the service
        }
    }
    
    @Override
    public String toString() {
        return String.format("DistributedLock[name=%s, value=%s, acquired=%s, released=%s, thread=%s]",
                lockName, lockValue.substring(0, 8) + "...", acquiredAt, released.get(), threadName);
    }
}