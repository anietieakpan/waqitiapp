package com.waqiti.common.bulkhead;

import lombok.Builder;
import lombok.Data;

/**
 * Statistics for bulkhead resource pools
 */
@Data
@Builder
public class BulkheadStatistics {
    
    private ResourceStats paymentProcessingStats;
    private ResourceStats kycVerificationStats;
    private ResourceStats fraudDetectionStats;
    private ResourceStats notificationStats;
    private ResourceStats analyticsStats;
    private ResourceStats coreBankingStats;
    
    public double getOverallUtilization() {
        double total = paymentProcessingStats.getUtilization() +
                      kycVerificationStats.getUtilization() +
                      fraudDetectionStats.getUtilization() +
                      notificationStats.getUtilization() +
                      analyticsStats.getUtilization() +
                      coreBankingStats.getUtilization();
        return total / 6.0;
    }
    
    public int getTotalActiveOperations() {
        return paymentProcessingStats.getInUse() +
               kycVerificationStats.getInUse() +
               fraudDetectionStats.getInUse() +
               notificationStats.getInUse() +
               analyticsStats.getInUse() +
               coreBankingStats.getInUse();
    }
    
    public int getTotalCapacity() {
        return paymentProcessingStats.getPoolSize() +
               kycVerificationStats.getPoolSize() +
               fraudDetectionStats.getPoolSize() +
               notificationStats.getPoolSize() +
               analyticsStats.getPoolSize() +
               coreBankingStats.getPoolSize();
    }
    
    public ResourceStats getMostUtilizedResource() {
        ResourceStats[] allStats = {
            paymentProcessingStats, kycVerificationStats, fraudDetectionStats,
            notificationStats, analyticsStats, coreBankingStats
        };
        
        ResourceStats mostUtilized = allStats[0];
        for (ResourceStats stats : allStats) {
            if (stats.getUtilization() > mostUtilized.getUtilization()) {
                mostUtilized = stats;
            }
        }
        return mostUtilized;
    }
}