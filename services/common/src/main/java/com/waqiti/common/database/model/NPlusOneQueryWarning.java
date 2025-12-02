package com.waqiti.common.database.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * N+1 query warning details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NPlusOneQueryWarning {
    
    public static class NPlusOneQueryWarningBuilder {
        public NPlusOneQueryWarningBuilder queryPattern(String pattern) {
            this.childQueryPattern = pattern;
            return this;
        }
        
        public NPlusOneQueryWarningBuilder executionCount(int count) {
            this.additionalQueries = count;
            return this;
        }
        
        public NPlusOneQueryWarningBuilder suggestion(String suggestion) {
            // Add suggestion to the suggestions list
            if (this.suggestions == null) {
                this.suggestions = new java.util.ArrayList<>();
            }
            this.suggestions.add(suggestion);
            return this;
        }
    }
    
    /**
     * Warning ID
     */
    private String warningId;
    
    /**
     * Method that triggered the warning
     */
    private String methodName;
    
    /**
     * Entity class involved
     */
    private String entityClass;
    
    /**
     * Number of additional queries
     */
    private int additionalQueries;
    
    /**
     * Parent query
     */
    private String parentQuery;
    
    /**
     * Child queries pattern
     */
    private String childQueryPattern;
    
    /**
     * Total execution time
     */
    private long totalExecutionTimeMs;
    
    /**
     * Timestamp of detection
     */
    private Instant detectedAt;
    
    /**
     * Severity level
     */
    private Severity severity;
    
    /**
     * Suggested optimizations
     */
    private List<String> suggestions;
    
    /**
     * Stack trace
     */
    private List<String> stackTrace;
    
    public enum Severity {
        LOW,
        WARNING,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}