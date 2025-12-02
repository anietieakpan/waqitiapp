package com.waqiti.common.database.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Index usage statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexUsageStats {
    
    /**
     * Index name
     */
    private String indexName;
    
    /**
     * Table name
     */
    private String tableName;
    
    /**
     * Schema name
     */
    private String schemaName;
    
    /**
     * Index type
     */
    private String indexType;
    
    /**
     * Columns indexed
     */
    private List<String> columns;
    
    /**
     * Usage statistics
     */
    private UsageMetrics usageMetrics;
    
    /**
     * Performance metrics
     */
    private PerformanceMetrics performanceMetrics;
    
    /**
     * Size information
     */
    private SizeInfo sizeInfo;
    
    /**
     * Maintenance info
     */
    private MaintenanceInfo maintenanceInfo;
    
    /**
     * Effectiveness score (0-100)
     */
    private double effectivenessScore;
    
    /**
     * Recommendations
     */
    private List<String> recommendations;
    
    /**
     * Last analyzed
     */
    private Instant lastAnalyzed;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsageMetrics {
        private long scanCount;
        private long seekCount;
        private long lookupCount;
        private long updateCount;
        private Instant lastUsed;
        private double usageFrequency;
        private boolean unused;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceMetrics {
        private double averageSeekTimeMs;
        private double averageScanTimeMs;
        private long rowsReturned;
        private double selectivityRatio;
        private long pageReads;
        private long pageWrites;
        private double cacheHitRatio;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SizeInfo {
        private long indexSizeBytes;
        private long leafPages;
        private long branchPages;
        private int indexDepth;
        private double fillFactor;
        private double fragmentationPercentage;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MaintenanceInfo {
        private Instant lastRebuild;
        private Instant lastReorganize;
        private boolean needsRebuild;
        private boolean needsReorganize;
        private long rebuildCount;
        private long reorganizeCount;
        private Map<String, Object> maintenanceHistory;
    }
}