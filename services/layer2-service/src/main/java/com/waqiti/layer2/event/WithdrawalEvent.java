package com.waqiti.layer2.event;

import com.waqiti.layer2.model.Layer2Solution;
import lombok.Builder;
import lombok.Data;

import java.math.BigInteger;
import java.time.Instant;

/**
 * Event published when withdrawal is initiated
 */
@Data
@Builder
public class WithdrawalEvent {
    private String withdrawalId;
    private String userAddress;
    private BigInteger amount;
    private Layer2Solution solution;
    private Instant estimatedCompletionTime;
    private Instant timestamp;
}
