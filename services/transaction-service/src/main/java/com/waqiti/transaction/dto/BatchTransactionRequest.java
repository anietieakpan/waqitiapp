package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BatchTransactionRequest {
    private String batchId;
    private List<ProcessTransactionRequest> transactions;
    private boolean stopOnError;
    private boolean validateBeforeProcessing;
    private String initiatedBy;
}