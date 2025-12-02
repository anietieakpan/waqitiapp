package com.waqiti.common.cdn;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Status of cache invalidations across CDN
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheInvalidationStatus {
    
    /**
     * Active invalidations
     */
    private List<InvalidationInfo> activeInvalidations;
    
    /**
     * Completed invalidations in the last 24 hours
     */
    private List<InvalidationInfo> recentCompletedInvalidations;
    
    /**
     * Failed invalidations in the last 24 hours
     */
    private List<InvalidationInfo> failedInvalidations;
    
    /**
     * Total invalidations today
     */
    private int invalidationsToday;
    
    /**
     * Total invalidations this month
     */
    private int invalidationsThisMonth;
    
    /**
     * Remaining invalidation quota
     */
    private int remainingQuota;
    
    /**
     * Monthly invalidation limit
     */
    private int monthlyLimit;
    
    /**
     * Estimated cost this month
     */
    private double estimatedMonthlyCost;
    
    /**
     * Invalidation statistics by distribution
     */
    private Map<String, DistributionStats> distributionStats;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvalidationInfo {
        
        public static class InvalidationInfoBuilder {
            public InvalidationInfoBuilder invalidationId(String id) {
                this.invalidationId = id;
                return this;
            }
        }
        private String invalidationId;
        private String distributionId;
        private CacheInvalidationResult.InvalidationStatus status;
        private List<String> paths;
        private Instant requestTime;
        private Instant completionTime;
        private int objectCount;
        private String requestedBy;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DistributionStats {
        private String distributionId;
        private String domainName;
        private int totalInvalidations;
        private int activeInvalidations;
        private Instant lastInvalidation;
        private double totalCost;
    }
}