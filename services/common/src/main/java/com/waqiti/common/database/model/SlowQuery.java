package com.waqiti.common.database.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Slow query information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlowQuery {
    
    /**
     * Query ID
     */
    private String queryId;
    
    /**
     * Query text
     */
    private String queryText;
    
    /**
     * Execution time in milliseconds
     */
    private long executionTimeMs;
    
    /**
     * Rows examined
     */
    private long rowsExamined;
    
    /**
     * Rows returned
     */
    private long rowsReturned;
    
    /**
     * Database name
     */
    private String database;
    
    /**
     * User who executed
     */
    private String executedBy;
    
    /**
     * Execution timestamp
     */
    private Instant executedAt;
    
    /**
     * Query plan
     */
    private String queryPlan;
    
    /**
     * Index usage
     */
    private boolean indexUsed;
    
    /**
     * Tables involved
     */
    private String[] tables;
    
    /**
     * Lock wait time
     */
    private long lockWaitTimeMs;
    
    /**
     * Additional context
     */
    private Map<String, Object> context;
}