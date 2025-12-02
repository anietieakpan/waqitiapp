package com.waqiti.crypto.dto;

import com.waqiti.crypto.entity.CryptoCurrency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * User Liquidity Position DTO
 * Represents a user's liquidity position in an AMM pool
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLiquidityPosition {
    
    private String userAddress;
    private CryptoCurrency token0;
    private CryptoCurrency token1;
    
    // Entry prices when liquidity was added
    private BigDecimal entryToken0Price;
    private BigDecimal entryToken1Price;
    
    // Initial amounts when liquidity was added
    private BigDecimal initialToken0Amount;
    private BigDecimal initialToken1Amount;
    
    // Current amounts (may differ due to swaps in pool)
    private BigDecimal token0Amount;
    private BigDecimal token1Amount;
    
    // Initial USD value when position opened
    private BigDecimal initialValueUSD;
    
    // When the position was opened
    private Long entryTimestamp;
    
    // Liquidity tokens received
    private BigDecimal liquidityTokens;
}