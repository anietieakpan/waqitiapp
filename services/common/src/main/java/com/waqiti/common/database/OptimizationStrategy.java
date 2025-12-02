package com.waqiti.common.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Represents a database query optimization strategy
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptimizationStrategy {
    
    private String strategyId;
    private String name;
    private String description;
    private StrategyType type;
    private String strategyType;
    private double estimatedImprovement;
    private double expectedImprovement; // Alias for estimatedImprovement
    private double implementationCost;
    private double riskLevel;
    private String complexity;  // Complexity level as string (LOW, MEDIUM, HIGH)
    private String risk;        // Risk level as string (LOW, MEDIUM, HIGH)
    private List<String> requiredIndexes;
    private List<String> requiredChanges;
    private Map<String, Object> parameters;
    private String implementationScript;
    private int executionOrder;
    private boolean autoApplicable;
    
    public enum StrategyType {
        INDEX_OPTIMIZATION,
        QUERY_REWRITE,
        TABLE_PARTITIONING,
        CACHING,
        CONNECTION_POOLING,
        MATERIALIZED_VIEW,
        DENORMALIZATION,
        BATCH_PROCESSING,
        PARALLEL_EXECUTION,
        RESOURCE_ALLOCATION
    }
}