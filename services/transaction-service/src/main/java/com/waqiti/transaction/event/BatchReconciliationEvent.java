package com.waqiti.transaction.event;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class BatchReconciliationEvent {
    private String batchId;
    private int reconciledCount;
    private int failedCount;
    private int discrepancyCount;
    private LocalDateTime completedAt;
}