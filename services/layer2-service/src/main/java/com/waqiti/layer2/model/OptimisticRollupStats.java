package com.waqiti.layer2.model;

import lombok.Builder;
import lombok.Data;

/**
 * Statistics for Optimistic Rollup
 */
@Data
@Builder
public class OptimisticRollupStats {
    private long totalTransactions;
    private long successfulBatches;
    private long challengedBatches;
    private int activeBatches;
    private int activeChallenges;
    private long challengePeriod;         // Seconds
    private double averageBatchSize;
    private long averageFinalizationTime; // Seconds
}
