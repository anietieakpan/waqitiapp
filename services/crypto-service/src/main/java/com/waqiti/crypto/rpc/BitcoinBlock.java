package com.waqiti.crypto.rpc;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class BitcoinBlock {
    private String hash;
    private long height;
    private int version;
    private String merkleRoot;
    private long timestamp;
    private long nonce;
    private BigDecimal difficulty;
    private int size;
    private int transactionCount;
    private String previousBlockHash;
    private String nextBlockHash;
    private List<String> transactionHashes;
}