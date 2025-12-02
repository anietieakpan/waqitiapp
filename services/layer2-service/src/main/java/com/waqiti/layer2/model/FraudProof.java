package com.waqiti.layer2.model;

import lombok.Builder;
import lombok.Data;

/**
 * Fraud proof for Optimistic Rollup challenges
 */
@Data
@Builder
public class FraudProof {
    private String batchId;
    private int invalidTransactionIndex;
    private String preStateRoot;
    private String postStateRoot;
    private byte[] proofData;
}
