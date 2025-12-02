package com.waqiti.common.metrics;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Metrics service summary and statistics
 */
@Data
@Builder
public class MetricsSummary {
    private boolean enabled;
    private String serviceName;
    private long activeTransactions;
    private long totalUsers;
    private long failedOperations;
    private int countersRegistered;
    private int timersRegistered;
    private int gaugesRegistered;
    private int summariesRegistered;
    private Instant lastUpdated;
    
    public int getTotalMetersRegistered() {
        return countersRegistered + timersRegistered + gaugesRegistered + summariesRegistered;
    }
    
    public boolean hasFailures() {
        return failedOperations > 0;
    }
}