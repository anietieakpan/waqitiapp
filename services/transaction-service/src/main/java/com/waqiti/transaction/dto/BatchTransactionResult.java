package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BatchTransactionResult {
    private String batchId;
    private int totalTransactions;
    private int successfulTransactions;
    private int failedTransactions;
    private List<TransactionProcessingResult> processingResults;
    private String errorMessage;
}