package com.waqiti.crypto.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.math.BigInteger;

@Data
@Builder
public class StakingStats {
    private BigInteger totalStaked;
    private BigInteger totalSupply;
    private BigDecimal stakingRatio;
    private BigDecimal currentApy;
    private BigInteger totalRewardsDistributed;
    private int totalStakers;
    private BigDecimal averageStakeAmount;
    private BigDecimal rewardRate;
    private BigInteger totalValueLocked;
    private int activeStakers;
}