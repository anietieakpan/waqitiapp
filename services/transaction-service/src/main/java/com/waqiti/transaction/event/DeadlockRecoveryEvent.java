package com.waqiti.transaction.event;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DeadlockRecoveryEvent {
    private String deadlockId;
    private String victimTransactionId;
    private boolean retryScheduled;
    private LocalDateTime recoveredAt;
}