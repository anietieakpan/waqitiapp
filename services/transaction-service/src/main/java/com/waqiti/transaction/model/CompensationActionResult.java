package com.waqiti.transaction.model;

import lombok.Builder;
import lombok.Data;

/**
 * Result of a single compensation action
 */
@Data
@Builder
public class CompensationActionResult {
    private String actionName;
    private boolean success;
    private String errorMessage;
    private long executionTimeMs;
    
    public static CompensationActionResult success(String actionName) {
        return CompensationActionResult.builder()
            .actionName(actionName)
            .success(true)
            .build();
    }
    
    public static CompensationActionResult failed(String errorMessage) {
        return CompensationActionResult.builder()
            .success(false)
            .errorMessage(errorMessage)
            .build();
    }
    
    public static CompensationActionResult timeout() {
        return CompensationActionResult.builder()
            .success(false)
            .errorMessage("Timeout waiting for compensation action")
            .build();
    }
}
