package com.waqiti.common.eventsourcing;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Event store statistics
 */
@Data
@Builder
public class EventStoreStatistics {
    
    private long totalEvents;
    private long totalSnapshots;
    private long totalAggregates;
    private int snapshotFrequency;
    private double cacheHitRatio;
    private String errorMessage;
    private Instant lastUpdated;
    
    public static EventStoreStatistics error(String errorMessage) {
        return EventStoreStatistics.builder()
            .errorMessage(errorMessage)
            .lastUpdated(Instant.now())
            .build();
    }
    
    public boolean hasError() {
        return errorMessage != null;
    }
}