package com.waqiti.transaction.saga;

import com.waqiti.transaction.dto.ProcessTransactionRequest;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BatchTransactionSaga {
    private String batchId;
    private List<ProcessTransactionRequest> transactions;
    private BatchSagaStatus status;
    private int currentTransactionIndex;
    private String failureReason;
    
    public enum BatchSagaStatus {
        PENDING, RUNNING, COMPLETED, FAILED, PARTIALLY_COMPLETED
    }
}