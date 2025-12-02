package com.waqiti.layer2.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Challenge to a Plasma exit
 */
@Data
@Builder
public class PlasmaChallenge {
    private String id;
    private String exitId;
    private String challengerAddress;
    private byte[] challengeProof;
    private LocalDateTime timestamp;
    private PlasmaChallengeStatus status;
    private String l1TransactionHash;
}
