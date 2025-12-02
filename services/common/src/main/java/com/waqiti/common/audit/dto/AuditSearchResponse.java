package com.waqiti.common.audit.dto;

import com.waqiti.common.audit.domain.AuditLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for audit log search operations
 * 
 * Provides comprehensive search results with pagination metadata,
 * aggregations, and query performance metrics.
 * 
 * FEATURES:
 * - Paginated results with navigation metadata
 * - Aggregations and statistics
 * - Query execution metrics
 * - Cache hit tracking
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditSearchResponse {
    
    /**
     * List of audit log results
     */
    private List<AuditLog> results;
    
    /**
     * Total number of elements across all pages
     */
    private long totalElements;
    
    /**
     * Total number of pages
     */
    private int totalPages;
    
    /**
     * Current page number (zero-based)
     */
    private int currentPage;
    
    /**
     * Number of elements per page
     */
    private int pageSize;
    
    /**
     * Whether there is a next page
     */
    private boolean hasNext;
    
    /**
     * Whether there is a previous page
     */
    private boolean hasPrevious;
    
    /**
     * Aggregations and statistics for the search results
     * 
     * May include:
     * - eventTypes: Map of event type counts
     * - severities: Map of severity level counts
     * - results: Map of operation result counts
     * - categories: Map of category counts
     * - timeDistribution: Events by time period
     * - userDistribution: Events by user
     * - ipDistribution: Events by IP address
     */
    private Map<String, Object> aggregations;
    
    /**
     * Metadata about the search execution
     */
    private AuditSearchMetadata metadata;
    
    /**
     * Metadata about search execution and performance
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditSearchMetadata {
        
        /**
         * Query execution time in milliseconds
         */
        private long executionTimeMs;
        
        /**
         * Number of filters applied in the search
         */
        private int filtersApplied;
        
        /**
         * Whether the result was served from cache
         */
        private boolean cacheHit;
        
        /**
         * Index or optimization hints used
         */
        private String indexUsed;
        
        /**
         * Total rows scanned for the query
         */
        private Long rowsScanned;
        
        /**
         * Query complexity score (1-10)
         */
        private Integer complexityScore;
        
        /**
         * Warning messages or optimization suggestions
         */
        private List<String> warnings;
        
        /**
         * Applied sort order
         */
        private String sortOrder;
    }
    
    /**
     * Check if results are empty
     */
    public boolean isEmpty() {
        return results == null || results.isEmpty();
    }
    
    /**
     * Get number of results in current page
     */
    public int getResultCount() {
        return results != null ? results.size() : 0;
    }
    
    /**
     * Get aggregation value by key
     */
    public Object getAggregation(String key) {
        return aggregations != null ? aggregations.get(key) : null;
    }
    
    /**
     * Check if aggregations are available
     */
    public boolean hasAggregations() {
        return aggregations != null && !aggregations.isEmpty();
    }
    
    /**
     * Get first result
     */
    public AuditLog getFirstResult() {
        return results != null && !results.isEmpty() ? results.get(0) : null;
    }
    
    /**
     * Get last result
     */
    public AuditLog getLastResult() {
        return results != null && !results.isEmpty() ? 
                results.get(results.size() - 1) : null;
    }
    
    /**
     * Calculate result range
     */
    public String getResultRange() {
        if (isEmpty()) {
            return "0-0 of 0";
        }
        int start = currentPage * pageSize + 1;
        int end = Math.min(start + getResultCount() - 1, (int) totalElements);
        return String.format("%d-%d of %d", start, end, totalElements);
    }
    
    /**
     * Check if query was slow (over 1 second)
     */
    public boolean isSlowQuery() {
        return metadata != null && metadata.getExecutionTimeMs() > 1000;
    }
    
    /**
     * Get human-readable execution time
     */
    public String getFormattedExecutionTime() {
        if (metadata == null) {
            return "N/A";
        }
        long ms = metadata.getExecutionTimeMs();
        if (ms < 1000) {
            return ms + " ms";
        } else {
            return String.format("%.2f s", ms / 1000.0);
        }
    }
}