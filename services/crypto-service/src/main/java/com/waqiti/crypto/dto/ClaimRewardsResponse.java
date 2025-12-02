package com.waqiti.crypto.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigInteger;

@Data
@Builder
public class ClaimRewardsResponse {
    private String transactionHash;
    private BigInteger rewardsClaimed;
    private BigInteger remainingRewards;
    private String status;
    private String message;
    private Long estimatedConfirmationTime;
    private boolean autoRestaked;
}