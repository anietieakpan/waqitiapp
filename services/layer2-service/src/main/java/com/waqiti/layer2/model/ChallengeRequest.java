package com.waqiti.layer2.model;

import lombok.Builder;
import lombok.Data;

/**
 * Request to challenge a batch
 */
@Data
@Builder
public class ChallengeRequest {
    private String batchId;
    private String challengerAddress;
    private ChallengeType challengeType;
    private String reason;
    private byte[] evidence;
}
