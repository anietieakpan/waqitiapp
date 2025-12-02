package com.waqiti.crypto.dto;

import com.waqiti.crypto.entity.CryptoCurrency;
import lombok.Builder;
import lombok.Data;

import java.math.BigInteger;

@Data
@Builder
public class AddLiquidityRequest {
    private CryptoCurrency token0;
    private CryptoCurrency token1;
    private BigInteger token0Amount;
    private BigInteger token1Amount;
    private String userAddress;
    private BigInteger deadline;
    private BigInteger slippageTolerance; // in basis points (100 = 1%)
}