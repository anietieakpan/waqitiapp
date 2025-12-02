package com.waqiti.common.messaging.deadletter;

/**
 * Dead Letter Queue Status Enumeration
 * 
 * Tracks the lifecycle status of messages in the dead letter queue.
 */
public enum DeadLetterStatus {
    
    /**
     * Message is pending processing or retry
     */
    PENDING,
    
    /**
     * Message is being retried
     */
    RETRYING,
    
    /**
     * Message is being manually reprocessed
     */
    REPROCESSING,
    
    /**
     * Message was successfully reprocessed
     */
    REPROCESSED,
    
    /**
     * Manual reprocessing failed
     */
    REPROCESS_FAILED,
    
    /**
     * Message was archived (too old or resolved)
     */
    ARCHIVED,
    
    /**
     * Message was manually discarded
     */
    DISCARDED,
    
    /**
     * Failed to send to DLQ topic
     */
    DLQ_SEND_FAILED,
    
    /**
     * Message identified as poison and quarantined
     */
    QUARANTINED
}