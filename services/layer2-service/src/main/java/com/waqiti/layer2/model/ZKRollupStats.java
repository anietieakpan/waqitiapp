package com.waqiti.layer2.model;

import lombok.Builder;
import lombok.Data;

/**
 * Statistics for ZK Rollup
 */
@Data
@Builder
public class ZKRollupStats {
    private long totalTransactions;
    private long successfulBatches;
    private long failedProofs;
    private int activeBatches;
    private int proofCacheSize;
    private long averageProofTime;    // Seconds
    private double averageBatchSize;
}
