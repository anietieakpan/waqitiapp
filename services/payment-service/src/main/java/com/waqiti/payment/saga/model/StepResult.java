package com.waqiti.payment.saga.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Result of executing a SAGA step
 */
@Data
@Builder
public class StepResult {
    
    private boolean success;
    
    private String errorMessage;
    
    private String errorCode;
    
    private Map<String, Object> data;
    
    private LocalDateTime completedAt;
    
    private long executionTimeMs;
    
    private boolean compensatable;
    
    public static StepResult success(Map<String, Object> data) {
        return StepResult.builder()
                .success(true)
                .data(data)
                .completedAt(LocalDateTime.now())
                .compensatable(true)
                .build();
    }
    
    public static StepResult failure(String errorMessage, String errorCode) {
        return StepResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .errorCode(errorCode)
                .completedAt(LocalDateTime.now())
                .compensatable(false)
                .build();
    }
}