package com.waqiti.layer2.model;

import com.waqiti.common.model.transaction.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigInteger;
import java.time.LocalDateTime;

/**
 * Base Layer 2 transaction model
 */
@Data
@Builder
public class Layer2Transaction {
    private String id;
    private String fromAddress;
    private String toAddress;
    private BigInteger amount;
    private TransactionType type;
    private Layer2Solution solution;
    private Layer2Status status;
    private LocalDateTime timestamp;
    private BigInteger nonce;
    private String hash;
    private BigInteger gasLimit;
    private BigInteger gasPrice;
}
