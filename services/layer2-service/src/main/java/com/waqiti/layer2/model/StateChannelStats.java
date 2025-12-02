package com.waqiti.layer2.model;

import lombok.Builder;
import lombok.Data;

/**
 * Statistics for State Channels
 */
@Data
@Builder
public class StateChannelStats {
    private long totalChannels;
    private long activeChannels;
    private long totalTransactions;
    private long totalDisputes;
    private long averageChannelLifetime;  // Seconds
    private double averageTransactionsPerChannel;
}
