package com.waqiti.layer2.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigInteger;
import java.time.LocalDateTime;

/**
 * Represents an update to a state channel
 */
@Data
@Builder
public class StateChannelUpdate {
    private String id;
    private String channelId;
    private String fromAddress;
    private String toAddress;
    private BigInteger amount;
    private BigInteger nonce;
    private LocalDateTime timestamp;
    private String stateHash;
    private String signature;
}
