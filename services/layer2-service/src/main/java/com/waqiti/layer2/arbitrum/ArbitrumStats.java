package com.waqiti.layer2.arbitrum;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Arbitrum network statistics
 */
@Data
@Builder
public class ArbitrumStats {
    private boolean connected;
    private long chainId;
    private BigInteger currentBlock;
    private BigInteger gasPrice;
    private BigDecimal gasPriceGwei;
    private String rpcUrl;
}
