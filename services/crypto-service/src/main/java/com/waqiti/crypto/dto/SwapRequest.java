package com.waqiti.crypto.dto;

import com.waqiti.crypto.entity.CryptoCurrency;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.math.BigInteger;

@Data
@Builder
public class SwapRequest {
    private CryptoCurrency tokenIn;
    private CryptoCurrency tokenOut;
    private BigInteger amountIn;
    private BigInteger amountOutMin;
    private String userAddress;
    private BigDecimal maxSlippage;
    private boolean exactInput;
    private BigInteger deadline;
}