package com.waqiti.layer2.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigInteger;
import java.time.Instant;

/**
 * Request to withdraw funds from Layer 2
 */
@Data
@Builder
public class WithdrawalRequest {
    private String userAddress;
    private String tokenAddress;
    private BigInteger amount;
    private Layer2Solution fromSolution;
    private Instant requestTime;
    private WithdrawalStatus status;
}
