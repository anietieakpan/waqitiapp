package com.waqiti.common.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Represents the analysis results of a database query
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryAnalysis {
    private String queryPattern;
    private QueryComplexity complexity;
    private List<String> tablesAccessed;
    private List<String> indexesUsed;
    private boolean isOptimizable;
    private List<OptimizationRecommendation> recommendations;
    private Map<String, Double> costBreakdown;
    private double estimatedCost;
    private String executionPlan;
    
    public enum QueryComplexity {
        SIMPLE,
        MODERATE,
        COMPLEX,
        VERY_COMPLEX
    }
}