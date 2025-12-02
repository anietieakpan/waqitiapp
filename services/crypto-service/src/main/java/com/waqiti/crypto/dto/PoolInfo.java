package com.waqiti.crypto.dto;

import com.waqiti.crypto.entity.CryptoCurrency;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.math.BigInteger;

@Data
@Builder
public class PoolInfo {
    private CryptoCurrency token0;
    private CryptoCurrency token1;
    private BigInteger reserve0;
    private BigInteger reserve1;
    private BigInteger totalLiquidity;
    private BigInteger kLast;
    private BigDecimal price;
    private BigDecimal tvl;
    private BigDecimal volume24h;
    private BigDecimal fees24h;
    private BigDecimal apy;
    private String poolAddress;
}