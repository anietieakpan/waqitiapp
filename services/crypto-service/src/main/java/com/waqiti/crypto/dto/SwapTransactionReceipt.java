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
public class SwapTransactionReceipt {
    
    private String transactionHash;
    
    private BigInteger amountIn;
    
    private BigInteger amountOut;
    
    private BigInteger fee;
    
    private BigInteger gasUsed;
    
    private BigInteger blockNumber;
    
    private String status;
}