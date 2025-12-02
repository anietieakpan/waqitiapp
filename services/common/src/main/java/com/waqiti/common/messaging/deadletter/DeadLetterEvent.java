package com.waqiti.common.messaging.deadletter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Dead Letter Event
 * 
 * Represents an event in the dead letter queue processing lifecycle.
 * Used for tracking and monitoring DLQ operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterEvent {
    
    private String eventId;
    private String messageId;
    private String topic;
    private String eventType; // SENT_TO_DLQ, RETRY_ATTEMPTED, MARKED_POISON, etc.
    private LocalDateTime timestamp;
    private String reason;
    private String errorMessage;
    private Integer retryAttempt;
    private String metadata;
    
    public enum EventType {
        SENT_TO_DLQ,
        RETRY_ATTEMPTED,
        MARKED_POISON,
        REPROCESSED_SUCCESS,
        REPROCESSED_FAILURE,
        MANUAL_INTERVENTION
    }
}