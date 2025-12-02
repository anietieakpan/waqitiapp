package com.waqiti.common.events.model;

/**
 * Event processing status for the outbox pattern
 * Used to track event lifecycle in the event-driven architecture
 */
public enum EventStatus {
    
    /**
     * Event is pending publication to Kafka
     */
    PENDING,
    
    /**
     * Event has been successfully sent to Kafka
     */
    SENT,
    
    /**
     * Event publication failed and needs retry
     */
    FAILED,
    
    /**
     * Event is scheduled for retry
     */
    RETRY_SCHEDULED,
    
    /**
     * Event has been moved to dead letter queue after max retries
     */
    DEAD_LETTERED,
    
    /**
     * Event processing is in progress
     */
    PROCESSING,
    
    /**
     * Event was successfully processed
     */
    PROCESSED,
    
    /**
     * Event was skipped due to business rules
     */
    SKIPPED
}