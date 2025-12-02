package com.waqiti.transaction.saga;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SagaStepExecutionResult {
    private String stepName;
    private boolean success;
    private String message;
    private String errorCode;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private long executionTimeMs;
    private boolean compensated;
}