package com.waqiti.common.performance;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Represents a slow database query
 */
@Data
@Builder
public class SlowQuery {
    
    private String query;
    private long executionTime;
    private LocalDateTime timestamp;
    private String table;
    private int rowsExamined;
    private int rowsReturned;
    private boolean usedIndex;
    private String user;
    
    public boolean isVerySlow() {
        return executionTime > 5000; // > 5 seconds
    }
    
    public boolean isSlow() {
        return executionTime > 1000; // > 1 second
    }
    
    public double getEfficiency() {
        if (rowsExamined == 0) return 100.0;
        return ((double) rowsReturned / rowsExamined) * 100;
    }
    
    public boolean isInefficient() {
        return getEfficiency() < 10.0; // Less than 10% efficiency
    }
}