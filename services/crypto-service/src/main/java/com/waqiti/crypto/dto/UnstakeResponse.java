package com.waqiti.crypto.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.math.BigInteger;

@Data
@Builder
public class UnstakeResponse {
    private String transactionHash;
    private BigInteger unstakedAmount;
    private BigInteger remainingStaked;
    private BigInteger rewardsEarned;
    private BigDecimal penalty;
    private String status;
    private String message;
    private Long estimatedConfirmationTime;
}