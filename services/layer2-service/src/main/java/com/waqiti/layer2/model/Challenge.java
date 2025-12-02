package com.waqiti.layer2.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Challenge to an Optimistic Rollup batch
 */
@Data
@Builder
public class Challenge {
    private String id;
    private String batchId;
    private String challenger;
    private ChallengeType challengeType;
    private FraudProof fraudProof;
    private LocalDateTime timestamp;
    private ChallengeStatus status;
    private String l1TransactionHash;
}
