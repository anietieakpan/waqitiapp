package com.waqiti.layer2.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Batch of ZK Rollup transactions
 */
@Data
@Builder
public class ZKBatch {
    private String id;
    private List<ZKTransaction> transactions;
    private ZKProof aggregateProof;
    private LocalDateTime timestamp;
    private Long blockNumber;
    private String stateRoot;
    private ZKBatchStatus status;
    private String l1TransactionHash;
}
