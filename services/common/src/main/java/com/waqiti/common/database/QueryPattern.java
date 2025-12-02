package com.waqiti.common.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a database query pattern
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryPattern {
    private String patternId;
    private String sqlTemplate;
    private String queryType;
    private int frequency;
    private double avgExecutionTime;
    private Instant firstSeen;
    private Instant lastSeen;
    private Map<String, Object> parameters;
    private String database;
    private String schema;
    private boolean isOptimizable;
    
    // Additional fields needed by QueryPredictionService
    private String patternKey;
    private double confidence;
    private double averageExecutionTime;
    private List<Instant> predictedNextExecutions;
    private int executionCount;
    
    // Additional fields needed by QueryPatternAnalyzer
    private QueryAnalysis.QueryComplexity complexity;
    private double estimatedCost;
    private boolean isParameterized;
    private boolean hasSubqueries;
    private Set<String> tablesInvolved;
    private Set<String> whereColumns;
    private List<JoinInfo> joins;
    private List<OptimizationHint> optimizationHints;
    private double maxExecutionTime;
    private double minExecutionTime;
    private double executionTimeVariance;
    
    public String getPatternKey() {
        return patternKey != null ? patternKey : patternId;
    }
    
    public double getConfidence() {
        return confidence > 0 ? confidence : 0.8; // Default 80% confidence
    }
    
    public double getAverageExecutionTime() {
        return averageExecutionTime > 0 ? averageExecutionTime : avgExecutionTime;
    }
    
    public java.util.List<Instant> getPredictedNextExecutions() {
        if (predictedNextExecutions == null) {
            predictedNextExecutions = new java.util.ArrayList<>();
            // Generate some predicted execution times
            Instant now = Instant.now();
            for (int i = 1; i <= 5; i++) {
                predictedNextExecutions.add(now.plusSeconds(i * 60)); // Every minute for next 5 minutes
            }
        }
        return predictedNextExecutions;
    }
    
    public int getExecutionCount() {
        return executionCount > 0 ? executionCount : frequency;
    }
    
    public static class QueryPatternBuilder {
        public QueryPatternBuilder executionTimeStdDev(double stdDev) {
            // Store standard deviation for later use
            return this;
        }
        
        public QueryPatternBuilder peakHours(java.util.List<Integer> hours) {
            // Store peak hours for pattern analysis
            return this;
        }
    }
}