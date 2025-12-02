package com.waqiti.common.locking;

/**
 * Exception thrown when distributed lock operations fail
 */
public class DistributedLockException extends RuntimeException {
    
    public DistributedLockException(String message) {
        super(message);
    }
    
    public DistributedLockException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public DistributedLockException(Throwable cause) {
        super(cause);
    }
}