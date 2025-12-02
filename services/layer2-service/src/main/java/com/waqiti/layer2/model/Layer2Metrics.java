package com.waqiti.layer2.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Performance metrics for Layer 2 solution
 */
@Data
@Builder
public class Layer2Metrics {
    private BigInteger averageCost;        // Wei
    private BigInteger averageLatency;     // Seconds
    private BigInteger throughput;         // Transactions per second
    private BigDecimal successRate;        // Percentage
}
