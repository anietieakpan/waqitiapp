package com.waqiti.common.lock;

/**
 * Exception thrown when distributed lock cannot be acquired
 *
 * This typically indicates:
 * 1. Resource is currently locked by another transaction
 * 2. Timeout waiting for lock to become available
 * 3. Redis connection issues
 *
 * @author Principal Software Engineer
 * @since 2.0.0
 */
public class LockAcquisitionException extends RuntimeException {

    private final String lockKey;
    private final long waitTimeMs;

    public LockAcquisitionException(String lockKey, long waitTimeMs) {
        super(String.format("Failed to acquire lock '%s' within %d ms", lockKey, waitTimeMs));
        this.lockKey = lockKey;
        this.waitTimeMs = waitTimeMs;
    }

    public LockAcquisitionException(String lockKey, long waitTimeMs, Throwable cause) {
        super(String.format("Failed to acquire lock '%s' within %d ms", lockKey, waitTimeMs), cause);
        this.lockKey = lockKey;
        this.waitTimeMs = waitTimeMs;
    }

    public String getLockKey() {
        return lockKey;
    }

    public long getWaitTimeMs() {
        return waitTimeMs;
    }
}
