package com.waqiti.transaction.event;

import com.waqiti.transaction.domain.Transaction;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TransactionEvent {
    private UUID transactionId;
    private String eventType;
    private LocalDateTime timestamp;
    private Transaction transactionData;
}