package com.waqiti.transaction.saga;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class TransactionSaga {
    private UUID transactionId;
    private String sagaId;
    private List<SagaStep> steps;
    private SagaStatus status;
    private int currentStepIndex;
    private String failureReason;
    
    public enum SagaStatus {
        PENDING, RUNNING, COMPLETED, FAILED, COMPENSATING, COMPENSATED
    }
}