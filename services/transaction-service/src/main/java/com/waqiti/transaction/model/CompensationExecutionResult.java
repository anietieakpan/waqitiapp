package com.waqiti.transaction.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Result of executing compensation actions
 */
@Data
@Builder
public class CompensationExecutionResult {
    private String sagaId;
    private int totalActions;
    private int successfulActions;
    private int failedActions;
    private List<CompensationActionResult> results;
    
    public boolean isFullySuccessful() {
        return failedActions == 0;
    }
    
    public double getSuccessRate() {
        return totalActions == 0 ? 1.0 : (double) successfulActions / totalActions;
    }
}
