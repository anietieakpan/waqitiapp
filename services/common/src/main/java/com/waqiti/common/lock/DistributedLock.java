package com.waqiti.common.lock;

import java.io.Closeable;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * CRITICAL PRODUCTION SERVICE: Distributed Lock Interface
 *
 * Provides distributed locking mechanism for preventing race conditions
 * in financial operations across multiple service instances.
 *
 * Implementation uses Redis/Redisson for distributed coordination.
 *
 * USAGE PATTERN:
 * try (DistributedLock lock = lockService.acquireLock("resource:id", 30, TimeUnit.SECONDS)) {
 *     // Critical section - protected by distributed lock
 *     performFinancialOperation();
 * }
 *
 * @author Principal Software Engineer
 * @since 2.0.0
 */
public interface DistributedLock extends Closeable, AutoCloseable {

    /**
     * Check if lock is currently held by this instance
     */
    boolean isLocked();

    /**
     * Get the lock key
     */
    String getKey();

    /**
     * Get remaining lock time in milliseconds
     */
    long getRemainingTimeToLive();

    /**
     * Manually unlock (usually not needed with try-with-resources)
     */
    void unlock();

    /**
     * Close is same as unlock (for AutoCloseable)
     */
    @Override
    default void close() {
        unlock();
    }
}
