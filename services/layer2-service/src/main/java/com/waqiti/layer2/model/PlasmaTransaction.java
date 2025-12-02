package com.waqiti.layer2.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigInteger;
import java.time.LocalDateTime;

/**
 * Plasma chain transaction
 */
@Data
@Builder
public class PlasmaTransaction {
    private String id;
    private String fromAddress;
    private String toAddress;
    private BigInteger amount;
    private LocalDateTime timestamp;
    private BigInteger nonce;
    private String hash;
    private PlasmaTransactionStatus status;
    private Long blockNumber;
}
