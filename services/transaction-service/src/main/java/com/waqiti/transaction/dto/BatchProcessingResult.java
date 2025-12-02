package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BatchProcessingResult {
    private String batchId;
    private int totalTransactions;
    private int successfulTransactions;
    private int failedTransactions;
    private List<TransactionProcessingResult> successfulResults;
    private List<TransactionProcessingResult> failedResults;
    private long processingTimeMs;
}