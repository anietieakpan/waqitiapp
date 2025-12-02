package com.waqiti.layer2.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigInteger;
import java.time.Instant;

/**
 * Result of withdrawal initiation
 */
@Data
@Builder
public class WithdrawalResult {
    private String withdrawalId;
    private String userAddress;
    private BigInteger amount;
    private Layer2Solution fromSolution;
    private String l1TransactionHash;
    private Instant estimatedCompletionTime;
    private WithdrawalStatus status;
    private Instant challengePeriodEnd;
}
