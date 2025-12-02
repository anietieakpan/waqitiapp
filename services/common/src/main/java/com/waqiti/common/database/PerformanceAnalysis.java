package com.waqiti.common.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Database performance analysis results
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceAnalysis {
    
    private String querySignature;
    private double averageExecutionTimeMs;
    private double minExecutionTimeMs;
    private double maxExecutionTimeMs;
    private long executionCount;
    private double cpuUsagePercentage;
    private double memoryUsagePercentage;
    private double ioWaitPercentage;
    private List<String> slowQueries;
    private List<String> missingIndexes;
    private List<String> unusedIndexes;
    private Map<String, Double> tableAccessPatterns;
    private List<PerformanceIssue> identifiedIssues;
    private List<String> bottlenecks;
    private LocalDateTime analysisTimestamp;
    
    // Methods needed by QueryExecutionPlanOptimizer
    public void setAverageExecutionTime(double time) {
        this.averageExecutionTimeMs = time;
    }
    
    public void setMaxExecutionTime(double time) {
        this.maxExecutionTimeMs = time;
    }
    
    public void setMinExecutionTime(double time) {
        this.minExecutionTimeMs = time;
    }
    
    public void addBottleneck(String bottleneck) {
        if (this.bottlenecks == null) {
            this.bottlenecks = new ArrayList<>();
        }
        this.bottlenecks.add(bottleneck);
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceIssue {
        private String issueType;
        private String description;
        private String severity;
        private String recommendation;
        private String affectedQuery;
        private double impactScore;
    }
}