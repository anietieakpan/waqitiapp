package com.waqiti.common.database.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * DTO for slow query information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlowQuery {
    
    private String queryId;
    private String normalizedQuery;
    private String rawQuery;
    private Duration executionTime;
    private Instant timestamp;
    private String database;
    private String username;
    private long rowsExamined;
    private long rowsReturned;
    private boolean indexUsed;
    private String executionPlan;
    private String tableName;
    private String operationType;
    private double cpuTime;
    private long memoryUsage;
    private String clientIp;
    private String applicationName;
    
    /**
     * Get severity level based on execution time
     */
    public QuerySeverity getSeverity() {
        if (executionTime.toSeconds() >= 60) {
            return QuerySeverity.CRITICAL;
        } else if (executionTime.toSeconds() >= 10) {
            return QuerySeverity.HIGH;
        } else if (executionTime.toSeconds() >= 5) {
            return QuerySeverity.MEDIUM;
        } else {
            return QuerySeverity.LOW;
        }
    }
    
    /**
     * Calculate efficiency score
     */
    public double getEfficiencyScore() {
        if (rowsExamined == 0) {
            return 1.0;
        }
        return (double) rowsReturned / rowsExamined;
    }
    
    /**
     * Check if query needs optimization
     */
    public boolean needsOptimization() {
        return !indexUsed || getEfficiencyScore() < 0.1 || executionTime.toSeconds() > 5;
    }
    
    public enum QuerySeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}