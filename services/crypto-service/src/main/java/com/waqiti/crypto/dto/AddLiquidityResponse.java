package com.waqiti.crypto.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.math.BigInteger;

@Data
@Builder
public class AddLiquidityResponse {
    private String transactionHash;
    private BigInteger token0Amount;
    private BigInteger token1Amount;
    private BigInteger liquidityTokens;
    private BigDecimal poolSharePercentage;
    private BigDecimal estimatedFees;
    private String status;
    private String poolId;
}