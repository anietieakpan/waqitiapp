package com.waqiti.common.database;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Metadata information about database tables
 */
@Data
@Builder
public class TableMetadata {
    
    private String tableName;
    private String schemaName;
    private String catalogName;
    private long rowCount;
    private long sizeInBytes;
    private List<ColumnMetadata> columns;
    private List<IndexMetadata> indexes;
    private List<String> primaryKeys;
    private List<String> foreignKeys;
    private Map<String, Object> statistics;
    private String creationDate;
    private String lastModified;
    
    @Data
    @Builder
    public static class ColumnMetadata {
        private String columnName;
        private String dataType;
        private int columnSize;
        private boolean nullable;
        private String defaultValue;
        private boolean isPrimaryKey;
        private boolean isForeignKey;
        private boolean isIndexed;
    }
}