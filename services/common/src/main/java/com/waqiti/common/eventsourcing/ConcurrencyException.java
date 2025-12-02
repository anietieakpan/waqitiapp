package com.waqiti.common.eventsourcing;

/**
 * Exception thrown when concurrency conflicts occur in event store
 */
public class ConcurrencyException extends RuntimeException {
    
    public ConcurrencyException(String message) {
        super(message);
    }
    
    public ConcurrencyException(String message, Throwable cause) {
        super(message, cause);
    }
}