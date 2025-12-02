package com.waqiti.layer2.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Batch of Optimistic Rollup transactions
 */
@Data
@Builder
public class OptimisticBatch {
    private String id;
    private List<OptimisticTransaction> transactions;
    private LocalDateTime timestamp;
    private Long blockNumber;
    private String stateRoot;
    private OptimisticBatchStatus status;
    private LocalDateTime challengePeriodEnd;
    private String l1TransactionHash;
}
