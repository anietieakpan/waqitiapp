package com.waqiti.layer2.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigInteger;
import java.time.LocalDateTime;

/**
 * State channel closure data
 */
@Data
@Builder
public class ChannelClosure {
    private String channelId;
    private BigInteger finalBalanceA;
    private BigInteger finalBalanceB;
    private BigInteger finalNonce;
    private LocalDateTime closureTime;
    private String signatureA;
    private String signatureB;
}
