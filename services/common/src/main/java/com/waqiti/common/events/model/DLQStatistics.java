package com.waqiti.common.events.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Statistics for Dead Letter Queue monitoring and alerting
 * Industrial-grade metrics for event-driven architecture monitoring
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DLQStatistics {
    
    private long totalEntries;
    private long unprocessedEntries;
    private long pendingRetryEntries;
    private long resolvedEntries;
    private long discardedEntries;
    private long manualReviewEntries;
    
    private Map<String, Long> entriesByTopic;
    private Map<String, Long> entriesByErrorType;
    private Map<String, Long> entriesByStatus;
    
    private LocalDateTime oldestEntry;
    private LocalDateTime newestEntry;
    
    private double averageRetryCount;
    private long maxRetryCount;
    
    private long entriesLast24Hours;
    private long entriesLast7Days;
    private long entriesLast30Days;
    
    private double resolutionRate;
    private double successfulRetryRate;
    
    private LocalDateTime statisticsGeneratedAt;
}