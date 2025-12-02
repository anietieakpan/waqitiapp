package com.waqiti.common.locking;

/**
 * CRITICAL SECURITY: Exception thrown when a distributed lock cannot be acquired
 * Used to signal lock timeout or acquisition failures
 */
public class LockAcquisitionException extends RuntimeException {
    
    public LockAcquisitionException(String message) {
        super(message);
    }
    
    public LockAcquisitionException(String message, Throwable cause) {
        super(message, cause);
    }
}