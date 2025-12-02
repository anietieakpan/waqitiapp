package com.waqiti.common.eventsourcing;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Result of batch event store operations
 */
@Data
@Builder
public class BatchEventStoreResult {
    
    private boolean success;
    private List<EventStoreResult> results;
    private String errorMessage;
    private int processedCount;
    
    public static BatchEventStoreResult success(List<EventStoreResult> results) {
        return BatchEventStoreResult.builder()
            .success(true)
            .results(results)
            .processedCount(results.size())
            .build();
    }
    
    public static BatchEventStoreResult failure(String errorMessage) {
        return BatchEventStoreResult.builder()
            .success(false)
            .errorMessage(errorMessage)
            .processedCount(0)
            .build();
    }
}