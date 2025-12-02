package com.waqiti.common.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a database query optimization recommendation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptimizationRecommendation {
    private OptimizationType type;
    private String description;
    private String suggestedAction;
    private String suggestedQuery;  // The actual SQL query to implement the recommendation
    private double expectedImprovement;
    private Priority priority;
    private String implementationCode;
    
    public enum OptimizationType {
        ADD_INDEX,
        CREATE_INDEX,
        REWRITE_QUERY,
        REORDER_JOINS,
        USE_CACHE,
        PARTITION_TABLE,
        DENORMALIZE,
        USE_MATERIALIZED_VIEW,
        BATCH_OPERATION,
        CONNECTION_POOLING,
        ADD_FILTER  // Add filter conditions to reduce dataset
    }
    
    public enum Priority {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}