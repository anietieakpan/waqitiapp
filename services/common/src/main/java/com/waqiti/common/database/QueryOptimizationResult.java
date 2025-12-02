package com.waqiti.common.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Result of query optimization analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryOptimizationResult {
    
    private String patternId;
    private QueryPattern originalPattern;
    private PerformanceAnalysis performanceAnalysis;
    private List<OptimizationStrategy> strategies;
    private OptimizationStrategy recommendedStrategy;
    private ImplementationPlan implementationPlan;
    private String error;
    
    private String originalQuery;
    private String optimizedQuery;
    private long originalExecutionTimeMs;
    private long optimizedExecutionTimeMs;
    private double improvementPercentage;
    private List<OptimizationRecommendation> recommendations;
    private List<String> appliedOptimizations;
    private QueryComplexity complexity;
    private Map<String, Object> executionPlan;
    private LocalDateTime analysisTimestamp;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptimizationRecommendation {
        private String type;
        private String description;
        private String impact;
        private int priority;
        private String sqlSuggestion;
        private String suggestedQuery;
        private List<String> requiredIndexes;
    }
    
    public enum QueryComplexity {
        SIMPLE,
        MODERATE,
        COMPLEX,
        VERY_COMPLEX
    }
}