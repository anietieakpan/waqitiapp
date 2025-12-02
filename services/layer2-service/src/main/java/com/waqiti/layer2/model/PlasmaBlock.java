package com.waqiti.layer2.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Plasma chain block
 */
@Data
@Builder
public class PlasmaBlock {
    private String id;
    private Long blockNumber;
    private List<PlasmaTransaction> transactions;
    private LocalDateTime timestamp;
    private Integer transactionCount;
    private String merkleRoot;
    private String parentHash;
    private String blockHash;
    private PlasmaBlockStatus status;
    private String l1TransactionHash;
}
