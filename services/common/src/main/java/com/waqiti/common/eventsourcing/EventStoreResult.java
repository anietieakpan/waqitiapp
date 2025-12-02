package com.waqiti.common.eventsourcing;

import lombok.Builder;
import lombok.Data;

/**
 * Result of event store operations
 */
@Data
@Builder
public class EventStoreResult {
    
    private boolean success;
    private Long eventId;
    private Long sequenceNumber;
    private String errorMessage;
    
    public static EventStoreResult success(Long eventId, Long sequenceNumber) {
        return EventStoreResult.builder()
            .success(true)
            .eventId(eventId)
            .sequenceNumber(sequenceNumber)
            .build();
    }
    
    public static EventStoreResult failure(String errorMessage) {
        return EventStoreResult.builder()
            .success(false)
            .errorMessage(errorMessage)
            .build();
    }
}