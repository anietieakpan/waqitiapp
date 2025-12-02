package com.waqiti.transaction.event;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class RetryScheduleEvent {
    private UUID transactionId;
    private String reason;
    private long retryDelay;
    private LocalDateTime scheduledAt;
}