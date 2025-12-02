package com.waqiti.common.database.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Database cache statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheStats {
    
    /**
     * Buffer cache stats
     */
    private BufferCacheStats bufferCache;
    
    /**
     * Query cache stats
     */
    private QueryCacheStats queryCache;
    
    /**
     * Result cache stats
     */
    private ResultCacheStats resultCache;
    
    /**
     * Plan cache stats
     */
    private PlanCacheStats planCache;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BufferCacheStats {
        private long sizeBytes;
        private long usedBytes;
        private long freeBytes;
        private double hitRatio;
        private long readRequests;
        private long writeRequests;
        private long evictions;
        private Map<String, Long> pageTypeBreakdown;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryCacheStats {
        private long entries;
        private long sizeBytes;
        private long hits;
        private long misses;
        private double hitRatio;
        private long inserts;
        private long evictions;
        private long invalidations;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResultCacheStats {
        private long entries;
        private long sizeBytes;
        private long hits;
        private long misses;
        private double hitRatio;
        private double averageResultSize;
        private Map<String, Long> resultTypeBreakdown;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanCacheStats {
        private long entries;
        private long sizeBytes;
        private long hits;
        private long misses;
        private double hitRatio;
        private long compilations;
        private long recompilations;
        private Map<String, Long> planTypeBreakdown;
    }
}