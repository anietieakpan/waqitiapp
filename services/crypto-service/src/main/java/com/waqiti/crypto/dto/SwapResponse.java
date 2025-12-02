package com.waqiti.crypto.dto;

import com.waqiti.crypto.entity.CryptoCurrency;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

@Data
@Builder
public class SwapResponse {
    private String transactionHash;
    private BigInteger amountIn;
    private BigInteger amountOut;
    private BigDecimal priceImpact;
    private BigInteger fee;
    private BigDecimal effectivePrice;
    private List<CryptoCurrency> route;
    private String status;
}