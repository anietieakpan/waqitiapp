package com.waqiti.common.database.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Query performance statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryPerformanceStats {
    
    public static class QueryPerformanceStatsBuilder {
        public QueryPerformanceStatsBuilder totalExecutions(long total) {
            this.totalQueries = total;
            return this;
        }
        
        public QueryPerformanceStatsBuilder averageExecutionTime(double avgTime) {
            // Create ExecutionTimeStats if needed
            if (this.executionTimeStats == null) {
                this.executionTimeStats = ExecutionTimeStats.builder()
                    .averageExecutionTimeMs(avgTime)
                    .build();
            }
            return this;
        }
        
        public QueryPerformanceStatsBuilder slowQueryCount(long count) {
            // Store slow query count - in a real implementation this would populate slowQueries list
            return this;
        }
    }
    
    /**
     * Total queries executed
     */
    private long totalQueries;
    
    /**
     * Query breakdown by type
     */
    private Map<QueryType, Long> queryTypeBreakdown;
    
    /**
     * Execution time statistics
     */
    private ExecutionTimeStats executionTimeStats;
    
    /**
     * Slow queries
     */
    private List<SlowQuery> slowQueries;
    
    /**
     * Most frequent queries
     */
    private List<FrequentQuery> frequentQueries;
    
    /**
     * Query cache statistics
     */
    private QueryCacheStats queryCacheStats;
    
    /**
     * Query plan statistics
     */
    private QueryPlanStats queryPlanStats;
    
    /**
     * Error statistics
     */
    private QueryErrorStats errorStats;
    
    /**
     * Timestamp
     */
    private Instant timestamp;
    
    /**
     * Get average execution time
     */
    public double getAverageExecutionTime() {
        if (executionTimeStats != null) {
            return executionTimeStats.getAverageExecutionTimeMs();
        }
        return 0.0;
    }
    
    /**
     * Get slow query count
     */
    public long getSlowQueryCount() {
        if (slowQueries != null) {
            return slowQueries.size();
        }
        return 0;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionTimeStats {
        private double averageExecutionTimeMs;
        private double maxExecutionTimeMs;
        private double minExecutionTimeMs;
        private double medianExecutionTimeMs;
        private double p95ExecutionTimeMs;
        private double p99ExecutionTimeMs;
        private Map<Integer, Long> executionTimeDistribution;
        
        public double getAverageTime() {
            return averageExecutionTimeMs;
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlowQuery {
        private String queryText;
        private String queryHash;
        private double executionTimeMs;
        private long executionCount;
        private Instant lastExecuted;
        private String queryPlan;
        private List<String> tables;
        private Map<String, Object> metadata;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FrequentQuery {
        private String queryText;
        private String queryHash;
        private long executionCount;
        private double averageExecutionTimeMs;
        private double totalExecutionTimeMs;
        private Instant firstSeen;
        private Instant lastSeen;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryCacheStats {
        private long cacheHits;
        private long cacheMisses;
        private double hitRatio;
        private long cacheSize;
        private long cacheEvictions;
        private double averageCacheEntrySize;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryPlanStats {
        private long fullScans;
        private long indexScans;
        private long sortOperations;
        private long joinOperations;
        private Map<String, Long> planTypeBreakdown;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryErrorStats {
        private long totalErrors;
        private Map<String, Long> errorTypeBreakdown;
        private List<QueryError> recentErrors;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryError {
        private String errorCode;
        private String errorMessage;
        private String queryText;
        private Instant timestamp;
        private Map<String, String> context;
    }
    
    public enum QueryType {
        SELECT,
        INSERT,
        UPDATE,
        DELETE,
        DDL,
        OTHER
    }
}