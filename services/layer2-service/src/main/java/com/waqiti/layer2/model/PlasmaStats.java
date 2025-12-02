package com.waqiti.layer2.model;

import lombok.Builder;
import lombok.Data;

/**
 * Statistics for Plasma Chain
 */
@Data
@Builder
public class PlasmaStats {
    private long totalBlocks;
    private long totalTransactions;
    private long activeExits;
    private long totalExits;
    private long activeChallenges;
    private double averageBlockTime;
    private double averageTransactionsPerBlock;
}
