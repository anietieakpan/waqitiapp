package com.waqiti.layer2.event;

import com.waqiti.common.model.transaction.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigInteger;

/**
 * Event published when a transaction is created
 */
@Data
@Builder
public class TransactionCreatedEvent {
    private String transactionId;
    private String fromAddress;
    private String toAddress;
    private BigInteger amount;
    private TransactionType type;
}
