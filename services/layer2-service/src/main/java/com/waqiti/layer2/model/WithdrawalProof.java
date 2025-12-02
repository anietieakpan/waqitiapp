package com.waqiti.layer2.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigInteger;

/**
 * Proof for withdrawal from Layer 2
 */
@Data
@Builder
public class WithdrawalProof {
    private String userAddress;
    private BigInteger amount;
    private byte[] proofData;
    private String merkleRoot;
}
