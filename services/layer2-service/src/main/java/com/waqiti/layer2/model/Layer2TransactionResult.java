package com.waqiti.layer2.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigInteger;
import java.time.Instant;

@Data
@Builder
public class Layer2TransactionResult {
    private String transactionHash;
    private Layer2Solution layer2Solution;
    private Layer2Status status;
    private Instant estimatedFinalityTime;
    private BigInteger gasUsed;
    private BigInteger fee;
    private String blockHash;
    private ZKProof zkProof;
    private StateChannelUpdate channelUpdate;
    private String errorMessage;
    private Long confirmations;
    private BigInteger actualFinalityTime;
}