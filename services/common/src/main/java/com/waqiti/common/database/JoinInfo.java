package com.waqiti.common.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Information about SQL joins in query execution
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JoinInfo {
    
    private String table;  // Primary table for the join
    private String leftTable;
    private String rightTable;
    private JoinType joinType;
    private List<String> joinColumns;
    private List<String> leftColumns;
    private List<String> rightColumns;
    private String condition;  // Alias for joinCondition
    private String joinCondition;
    private long estimatedRows;
    private double selectivity;
    private boolean indexExists;
    private String suggestedIndex;
    private long executionTimeMs;
    
    // Helper methods for backward compatibility
    public String getCondition() {
        return joinCondition != null ? joinCondition : condition;
    }
    
    public String getTable() {
        return table != null ? table : rightTable;
    }
    
    public enum JoinType {
        INNER,
        LEFT_OUTER,
        RIGHT_OUTER,
        FULL_OUTER,
        CROSS,
        NATURAL,
        SELF
    }
}