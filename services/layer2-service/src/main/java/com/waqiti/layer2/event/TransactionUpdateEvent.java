package com.waqiti.layer2.event;

import com.waqiti.common.model.transaction.TransactionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Event published when transaction status is updated
 */
@Data
@Builder
public class TransactionUpdateEvent {
    private String transactionId;
    private TransactionStatus status;
    private Instant timestamp;
    private String layer;
}
