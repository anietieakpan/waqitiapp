package com.waqiti.common.metrics.abstraction;

import lombok.Builder;
import lombok.Data;

/**
 * Metrics System Statistics
 */
@Data
@Builder
public class MetricsStats {
    private final long totalRecorded;
    private final long totalDropped;
    private final long totalErrors;
    private final long currentCardinality;
    private final int maxCardinality;
    private final boolean circuitBreakerOpen;
    private final int cacheSize;
    
    public double getDropRate() {
        long total = totalRecorded + totalDropped;
        return total > 0 ? (double) totalDropped / total : 0.0;
    }
    
    public double getErrorRate() {
        long total = totalRecorded + totalErrors;
        return total > 0 ? (double) totalErrors / total : 0.0;
    }
    
    public double getCardinalityUsage() {
        return maxCardinality > 0 ? (double) currentCardinality / maxCardinality : 0.0;
    }
}