package com.waqiti.layer2.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigInteger;
import java.time.LocalDateTime;

/**
 * Optimistic Rollup transaction
 */
@Data
@Builder
public class OptimisticTransaction {
    private String id;
    private String fromAddress;
    private String toAddress;
    private BigInteger amount;
    private LocalDateTime timestamp;
    private BigInteger nonce;
    private BigInteger gasLimit;
    private BigInteger gasPrice;
    private OptimisticTransactionStatus status;
}
