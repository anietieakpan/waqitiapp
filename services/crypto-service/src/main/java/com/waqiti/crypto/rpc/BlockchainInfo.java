package com.waqiti.crypto.rpc;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class BlockchainInfo {
    private String chain;
    private long blocks;
    private long headers;
    private String bestBlockHash;
    private BigDecimal difficulty;
    private long medianTime;
    private double verificationProgress;
    private boolean initialBlockDownload;
    private String chainwork;
    private long size;
    private boolean pruned;
}