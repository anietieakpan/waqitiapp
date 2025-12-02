package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BatchProcessingRequest {
    private String batchId;
    private List<ProcessTransactionRequest> transactions;
    private boolean parallelProcessing;
    private int maxBatchSize;
    private long delayBetweenTransactions;
    private long startTime;
}