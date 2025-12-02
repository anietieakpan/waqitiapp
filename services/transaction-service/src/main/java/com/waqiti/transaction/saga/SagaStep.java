package com.waqiti.transaction.saga;

import lombok.Builder;
import lombok.Data;

import java.util.function.Supplier;

@Data
@Builder
public class SagaStep {
    private String stepName;
    private Supplier<SagaStepResult> forwardAction;
    private Supplier<SagaStepResult> compensationAction;
    private int retryCount;
    private long retryDelayMs;
    private boolean critical;
}