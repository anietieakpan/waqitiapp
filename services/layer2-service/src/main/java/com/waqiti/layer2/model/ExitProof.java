package com.waqiti.layer2.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigInteger;

/**
 * Proof for exiting from Plasma or other Layer 2
 */
@Data
@Builder
public class ExitProof {
    private String userAddress;
    private BigInteger amount;
    private byte[] proofData;
    private Long blockNumber;
    private Integer transactionIndex;
}
