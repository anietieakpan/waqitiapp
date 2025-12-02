package com.waqiti.analytics.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Stream processing statistics model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamProcessingStats {
    
    private Integer totalBufferedEvents;
    private Integer totalAggregates;
    private Integer bufferKeys;
    private Instant lastProcessedAt;
}