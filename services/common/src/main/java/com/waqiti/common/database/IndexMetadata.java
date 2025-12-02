package com.waqiti.common.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Metadata information about database indexes
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexMetadata {
    
    private String indexName;
    private String tableName;
    private String schemaName;
    private String columnName;  // Single column name for simple indexes
    private List<String> columnNames;
    private IndexType indexType;
    private boolean unique;
    private boolean clustered;
    private long sizeInBytes;
    private long rowCount;
    private double selectivity;
    private int height;
    private int ordinalPosition;  // Position of column in multi-column index
    private String creationDate;
    private String lastAnalyzed;
    private IndexUsageStats usageStats;
    
    // Helper method for backward compatibility
    public String getColumnName() {
        if (columnName != null) return columnName;
        if (columnNames != null && !columnNames.isEmpty()) return columnNames.get(0);
        return null;
    }
    
    public enum IndexType {
        BTREE,
        HASH,
        GIN,
        GIST,
        BRIN,
        BITMAP,
        CLUSTERED,
        NONCLUSTERED
    }
    
    @Data
    @Builder
    public static class IndexUsageStats {
        private long totalReads;
        private long totalWrites;
        private double averageKeyLength;
        private double fillFactor;
        private long lastUsed;
    }
}