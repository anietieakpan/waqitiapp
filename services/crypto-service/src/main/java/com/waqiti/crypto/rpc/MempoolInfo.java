package com.waqiti.crypto.rpc;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class MempoolInfo {
    private int size;
    private long bytes;
    private long usage;
    private long maxMempool;
    private BigDecimal mempoolMinFee;
    private BigDecimal minRelayTxFee;
}