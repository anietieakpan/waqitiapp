package com.waqiti.transaction.saga;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class SagaExecutionResult {
    private boolean success;
    private String sagaId;
    private String executionSummary;
    private String failureReason;
    private List<SagaStepExecutionResult> stepResults;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private long executionTimeMs;
    private Map<String, Object> metadata;
}