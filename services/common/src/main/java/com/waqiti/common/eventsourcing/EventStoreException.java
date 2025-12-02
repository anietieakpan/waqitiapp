package com.waqiti.common.eventsourcing;

/**
 * Exception thrown by event store operations
 */
public class EventStoreException extends RuntimeException {
    
    public EventStoreException(String message) {
        super(message);
    }
    
    public EventStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}