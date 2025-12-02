package com.waqiti.common.database.performance.models;

import lombok.Data;

import java.time.Instant;

/**
 * Statistics for database index usage tracking.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
public class IndexUsageStats {
    
    private String tableName;
    private String indexName;
    private long usageCount;
    private double scanRatio;
    private long tuplesRead;
    private long tuplesFetched;
    private double selectivity;
    private long sizeBytes;
    private Instant lastUpdated;
    
    /**
     * Calculate index efficiency based on usage patterns.
     *
     * @return efficiency score between 0 and 1
     */
    public double calculateEfficiency() {
        if (usageCount == 0) {
            return 0.0;
        }
        
        // Factor in scan ratio and selectivity
        double efficiencyScore = scanRatio * 0.6 + selectivity * 0.4;
        
        // Penalize unused indexes
        if (usageCount < 10) {
            efficiencyScore *= 0.5;
        }
        
        return Math.min(1.0, efficiencyScore);
    }
    
    /**
     * Determine if this index is underutilized.
     *
     * @return true if index appears to be unused or inefficient
     */
    public boolean isUnderutilized() {
        return usageCount < 100 && calculateEfficiency() < 0.3;
    }
    
    /**
     * Determine if this index is critical for performance.
     *
     * @return true if index is heavily used and efficient
     */
    public boolean isCritical() {
        return usageCount > 10000 && calculateEfficiency() > 0.8;
    }
}