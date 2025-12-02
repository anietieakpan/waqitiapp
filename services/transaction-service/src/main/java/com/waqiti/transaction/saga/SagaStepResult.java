package com.waqiti.transaction.saga;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SagaStepResult {
    private boolean success;
    private String message;
    private String errorCode;
    private LocalDateTime executedAt;
    private long executionTimeMs;
    
    public static SagaStepResult success(String message) {
        return SagaStepResult.builder()
            .success(true)
            .message(message)
            .executedAt(LocalDateTime.now())
            .build();
    }
    
    public static SagaStepResult failure(String message) {
        return SagaStepResult.builder()
            .success(false)
            .message(message)
            .executedAt(LocalDateTime.now())
            .build();
    }
}