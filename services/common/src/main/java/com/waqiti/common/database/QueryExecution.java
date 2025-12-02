package com.waqiti.common.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Query execution information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryExecution {
    
    /**
     * Query ID
     */
    private String queryId;
    
    /**
     * Query text
     */
    private String queryText;
    
    /**
     * Query type
     */
    private QueryType queryType;
    
    /**
     * Start time
     */
    private Instant startTime;
    
    /**
     * End time
     */
    private Instant endTime;
    
    /**
     * Execution time in milliseconds
     */
    private long executionTimeMs;
    
    /**
     * Rows affected/returned
     */
    private long rowsAffected;
    
    /**
     * Was query optimized
     */
    private boolean optimized;
    
    /**
     * Optimization applied
     */
    private String optimizationApplied;
    
    /**
     * Query plan
     */
    private String queryPlan;
    
    /**
     * Database name
     */
    private String databaseName;
    
    // Explicit getters for compilation issues
    public String getQueryId() { return queryId; }
    public String getQueryText() { return queryText; }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public boolean isOptimized() { return optimized; }
    
    /**
     * User who executed
     */
    private String executedBy;
    
    /**
     * Execution context
     */
    private Map<String, Object> context;
    
    /**
     * Error information if failed
     */
    private String errorMessage;
    
    /**
     * Success status
     */
    private boolean successful;
    
    public enum QueryType {
        SELECT,
        INSERT,
        UPDATE,
        DELETE,
        DDL,
        PROCEDURE,
        FUNCTION,
        OTHER
    }
    
    /**
     * Get timestamp (alias for startTime)
     */
    public Instant getTimestamp() {
        return startTime;
    }
    
    /**
     * Get execution time (alias for executionTimeMs)
     */
    public long getExecutionTime() {
        return executionTimeMs;
    }
    
    /**
     * Get query (alias for queryText)
     */
    public String getQuery() {
        return queryText;
    }
}