package com.waqiti.layer2.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigInteger;
import java.time.LocalDateTime;

/**
 * ZK Rollup transaction
 */
@Data
@Builder
public class ZKTransaction {
    private String id;
    private String fromAddress;
    private String toAddress;
    private BigInteger amount;
    private ZKProof proof;
    private LocalDateTime timestamp;
    private BigInteger nonce;
    private BigInteger gasLimit;
    private BigInteger gasPrice;
    private ZKTransactionStatus status;
}
