package com.waqiti.common.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents a database index recommendation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexRecommendation {
    private String indexName;
    private String tableName;
    private List<String> columns;
    private IndexType indexType;
    private double expectedImprovementPercent;
    private long estimatedCreationTimeMs;
    private long estimatedStorageBytes;
    private double confidence;
    private String justification;
    private QueryPattern triggeringPattern;
    private Instant createdAt;
    private RecommendationStatus status;
    private Map<String, Object> metadata;
    
    public enum IndexType {
        BTREE("B-Tree"),
        HASH("Hash"),
        GIN("GIN"),
        GIST("GiST"),
        BRIN("BRIN"),
        PARTIAL("Partial"),
        UNIQUE("Unique"),
        COMPOSITE("Composite");
        
        private final String displayName;
        
        IndexType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    public enum RecommendationStatus {
        PENDING,
        APPROVED,
        REJECTED,
        IMPLEMENTED,
        FAILED
    }
    
    public enum QueryPattern {
        SIMPLE("Simple Query"),
        FILTERED_SELECT("Filtered Select"),
        JOIN("Join Query"),
        SORTED("Sorted Query"),
        AGGREGATION("Aggregation Query"),
        COMPLEX_JOIN("Complex Join"),
        SUBQUERY("Subquery"),
        UNION("Union Query");
        
        private final String displayName;
        
        QueryPattern(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    public static IndexRecommendation createBTreeRecommendation(String tableName, List<String> columns, 
                                                              QueryPattern pattern, double improvement) {
        return IndexRecommendation.builder()
            .indexName(generateIndexName(tableName, columns, IndexType.BTREE))
            .tableName(tableName)
            .columns(columns)
            .indexType(IndexType.BTREE)
            .expectedImprovementPercent(improvement)
            .estimatedCreationTimeMs(calculateEstimatedCreationTime(columns.size()))
            .estimatedStorageBytes(calculateEstimatedStorage(columns.size()))
            .confidence(0.85)
            .justification(generateJustification(IndexType.BTREE, columns, improvement))
            .triggeringPattern(pattern)
            .createdAt(Instant.now())
            .status(RecommendationStatus.PENDING)
            .build();
    }
    
    public static IndexRecommendation createCompositeRecommendation(String tableName, List<String> columns,
                                                                  QueryPattern pattern, double improvement) {
        return IndexRecommendation.builder()
            .indexName(generateIndexName(tableName, columns, IndexType.COMPOSITE))
            .tableName(tableName)
            .columns(columns)
            .indexType(IndexType.COMPOSITE)
            .expectedImprovementPercent(improvement)
            .estimatedCreationTimeMs(calculateEstimatedCreationTime(columns.size()) * 2)
            .estimatedStorageBytes((long)(calculateEstimatedStorage(columns.size()) * 1.5))
            .confidence(0.75)
            .justification(generateJustification(IndexType.COMPOSITE, columns, improvement))
            .triggeringPattern(pattern)
            .createdAt(Instant.now())
            .status(RecommendationStatus.PENDING)
            .build();
    }
    
    private static String generateIndexName(String tableName, List<String> columns, IndexType type) {
        String columnStr = String.join("_", columns);
        return String.format("idx_%s_%s_%s", tableName, columnStr, type.name().toLowerCase());
    }
    
    private static long calculateEstimatedCreationTime(int columnCount) {
        // Base time of 10 seconds, plus 5 seconds per additional column
        return 10000L + (columnCount - 1) * 5000L;
    }
    
    private static long calculateEstimatedStorage(int columnCount) {
        // Base storage of 1MB, plus 500KB per additional column
        return 1_000_000L + (columnCount - 1) * 500_000L;
    }
    
    private static String generateJustification(IndexType type, List<String> columns, double improvement) {
        return String.format("Creating %s index on columns [%s] is expected to improve query performance by %.1f%%",
            type.getDisplayName(), String.join(", ", columns), improvement);
    }
    
    public boolean isHighImpact() {
        return expectedImprovementPercent >= 50.0;
    }
    
    public boolean isHighConfidence() {
        return confidence >= 0.8;
    }
    
    public boolean shouldImplement() {
        return isHighConfidence() && expectedImprovementPercent >= 20.0;
    }
    
    public double getImpactScore() {
        return expectedImprovementPercent * confidence;
    }
    
    public String getIndexDefinition() {
        StringBuilder def = new StringBuilder();
        def.append("CREATE ");
        if (indexType == IndexType.UNIQUE) {
            def.append("UNIQUE ");
        }
        def.append("INDEX ").append(indexName);
        def.append(" ON ").append(tableName);
        def.append(" (").append(String.join(", ", columns)).append(")");
        return def.toString();
    }
}