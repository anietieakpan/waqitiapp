package com.waqiti.layer2.event;

import com.waqiti.layer2.model.Layer2Solution;
import com.waqiti.layer2.model.Layer2Status;
import lombok.Builder;
import lombok.Data;

import java.math.BigInteger;
import java.time.Instant;

/**
 * Event published when Layer 2 transaction is processed
 */
@Data
@Builder
public class Layer2TransactionEvent {
    private String transactionHash;
    private Layer2Solution solution;
    private Layer2Status status;
    private BigInteger gasUsed;
    private BigInteger fee;
    private Instant timestamp;
}
