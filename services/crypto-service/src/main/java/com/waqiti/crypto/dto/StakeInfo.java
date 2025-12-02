package com.waqiti.crypto.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;

@Data
@Builder
public class StakeInfo {
    private String userAddress;
    private BigInteger stakedAmount;
    private BigInteger pendingRewards;
    private BigDecimal apy;
    private LocalDateTime stakeTime;
    private LocalDateTime unlockTime;
    private boolean canUnstake;
    private BigDecimal totalRewardsEarned;
}