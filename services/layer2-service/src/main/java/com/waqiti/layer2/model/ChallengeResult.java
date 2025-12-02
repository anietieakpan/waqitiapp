package com.waqiti.layer2.model;

import lombok.Builder;
import lombok.Data;

/**
 * Result of challenging a batch
 */
@Data
@Builder
public class ChallengeResult {
    private String challengeId;
    private String batchId;
    private ChallengeStatus status;
    private String l1TransactionHash;
    private FraudProof fraudProof;
}
