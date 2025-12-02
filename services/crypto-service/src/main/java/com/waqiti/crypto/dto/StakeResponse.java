package com.waqiti.crypto.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;

@Data
@Builder
public class StakeResponse {
    private String transactionHash;
    private BigInteger stakedAmount;
    private BigInteger totalStaked;
    private BigDecimal expectedAnnualRewards;
    private BigDecimal apy;
    private LocalDateTime unlockDate;
    private String status;
}