package com.waqiti.common.distributed;

/**
 * Exception thrown when distributed lock acquisition times out.
 * 
 * @author Waqiti Security Team
 * @version 2.0.0
 * @since 2025-09-16
 */
public class LockTimeoutException extends RuntimeException {
    
    private final String lockName;
    private final int waitTimeSeconds;
    
    public LockTimeoutException(String lockName, int waitTimeSeconds) {
        super(String.format("Failed to acquire lock '%s' within %d seconds", lockName, waitTimeSeconds));
        this.lockName = lockName;
        this.waitTimeSeconds = waitTimeSeconds;
    }
    
    public LockTimeoutException(String message) {
        super(message);
        this.lockName = null;
        this.waitTimeSeconds = 0;
    }
    
    public LockTimeoutException(String message, Throwable cause) {
        super(message, cause);
        this.lockName = null;
        this.waitTimeSeconds = 0;
    }
    
    public String getLockName() {
        return lockName;
    }
    
    public int getWaitTimeSeconds() {
        return waitTimeSeconds;
    }
}