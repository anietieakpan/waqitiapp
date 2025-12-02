package com.waqiti.crypto.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigInteger;
import java.time.LocalDateTime;

@Data
@Builder
public class StakingHistoryEntry {
    private Type type;
    private BigInteger amount;
    private LocalDateTime timestamp;
    private String transactionHash;
    private BigInteger rewards;
    private String status;
    private String blockNumber;
    
    public enum Type {
        STAKE,
        UNSTAKE,
        CLAIM_REWARDS,
        RESTAKE
    }
}