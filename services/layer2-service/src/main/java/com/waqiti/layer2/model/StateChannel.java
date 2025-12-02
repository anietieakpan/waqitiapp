package com.waqiti.layer2.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigInteger;
import java.time.LocalDateTime;

/**
 * State Channel for off-chain instant payments
 */
@Data
@Builder
public class StateChannel {
    private String id;
    private String partyA;
    private String partyB;
    private BigInteger balanceA;
    private BigInteger balanceB;
    private BigInteger nonce;
    private ChannelStatus status;
    private LocalDateTime openTime;
    private LocalDateTime timeout;
    private LocalDateTime lastUpdate;
    private String l1OpenTxHash;
    private String l1CloseTxHash;
}
