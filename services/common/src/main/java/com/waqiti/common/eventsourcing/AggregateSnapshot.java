package com.waqiti.common.eventsourcing;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Aggregate snapshot data model
 */
@Data
@Builder
public class AggregateSnapshot {
    
    private String aggregateId;
    private Long version;
    private String snapshotData;
    private Instant timestamp;
    private Long eventCount;
}