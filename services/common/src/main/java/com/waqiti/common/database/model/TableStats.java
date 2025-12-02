package com.waqiti.common.database.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Table statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableStats {
    
    /**
     * Table name
     */
    private String tableName;
    
    /**
     * Schema name
     */
    private String schemaName;
    
    /**
     * Row count
     */
    private long rowCount;
    
    /**
     * Table size in bytes
     */
    private long tableSizeBytes;
    
    /**
     * Index size in bytes
     */
    private long indexSizeBytes;
    
    /**
     * Total size in bytes
     */
    private long totalSizeBytes;
    
    /**
     * Access statistics
     */
    private AccessStats accessStats;
    
    /**
     * Modification statistics
     */
    private ModificationStats modificationStats;
    
    /**
     * Column statistics
     */
    private List<ColumnStats> columnStats;
    
    /**
     * Index information
     */
    private List<IndexInfo> indexes;
    
    /**
     * Fragmentation info
     */
    private FragmentationInfo fragmentation;
    
    /**
     * Last analyzed
     */
    private Instant lastAnalyzed;
    
    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccessStats {
        private long sequentialScans;
        private long indexScans;
        private long rowsRead;
        private long rowsFetched;
        private Instant lastAccessed;
        private double cacheHitRatio;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModificationStats {
        private long insertsCount;
        private long updatesCount;
        private long deletesCount;
        private Instant lastModified;
        private long deadRows;
        private long liveRows;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ColumnStats {
        private String columnName;
        private String dataType;
        private long distinctValues;
        private double nullFraction;
        private double averageWidth;
        private String minValue;
        private String maxValue;
        private Map<String, Long> histogram;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndexInfo {
        private String indexName;
        private String indexType;
        private List<String> columns;
        private long sizeBytes;
        private boolean unique;
        private boolean primary;
        private long scansCount;
        private long rowsRead;
        private double effectiveness;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FragmentationInfo {
        private double fragmentationPercentage;
        private long wastedSpaceBytes;
        private boolean needsVacuum;
        private boolean needsReindex;
        private Instant lastVacuum;
        private Instant lastAutoVacuum;
    }
}