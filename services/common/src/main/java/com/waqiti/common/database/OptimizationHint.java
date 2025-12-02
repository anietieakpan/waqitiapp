package com.waqiti.common.database;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Query optimization hints and suggestions
 */
@Data
@Builder
public class OptimizationHint {
    
    private String hintType;
    private String description;
    private String recommendation;
    private HintPriority priority;
    private double expectedImprovement;
    private List<String> applicableQueries;
    private String sqlHint;
    private List<String> requiredIndexes;
    private String implementation;
    private List<String> considerations;
    
    public enum HintPriority {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}