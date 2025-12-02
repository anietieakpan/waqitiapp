package com.waqiti.layer2.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigInteger;

/**
 * Aggregated Layer 2 statistics
 */
@Data
@Builder
public class Layer2Statistics {
    private OptimisticRollupStats optimisticRollupStats;
    private ZKRollupStats zkRollupStats;
    private StateChannelStats stateChannelStats;
    private PlasmaStats plasmaStats;
    private Long totalThroughput;
    private BigInteger averageLatency;
    private BigInteger costSavings;
}
