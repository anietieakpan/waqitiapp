package com.waqiti.crypto.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiquidityTransactionReceipt {
    
    private String transactionHash;
    
    private BigInteger liquidityMinted;
    
    private BigInteger token0Amount;
    
    private BigInteger token1Amount;
    
    private BigInteger fees;
    
    private BigInteger gasUsed;
    
    private BigInteger blockNumber;
    
    private String status;
}