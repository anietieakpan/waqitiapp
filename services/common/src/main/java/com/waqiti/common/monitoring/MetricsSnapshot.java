package com.waqiti.common.monitoring;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Snapshot of current metrics for health checks and monitoring
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricsSnapshot {
    
    private long activeUsers;
    private long pendingTransactions;
    private long dailyVolume;
    private double totalPayments;
    private double totalFraudDetections;
    private double totalAuthFailures;
    private long timestamp;
    
    public MetricsSnapshot(long activeUsers, long pendingTransactions, long dailyVolume, 
                          double totalPayments, double totalFraudDetections, double totalAuthFailures) {
        this.activeUsers = activeUsers;
        this.pendingTransactions = pendingTransactions;
        this.dailyVolume = dailyVolume;
        this.totalPayments = totalPayments;
        this.totalFraudDetections = totalFraudDetections;
        this.totalAuthFailures = totalAuthFailures;
        this.timestamp = System.currentTimeMillis();
    }
}