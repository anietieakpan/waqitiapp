package com.waqiti.transaction.event;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class BatchEvent {
    private String batchId;
    private String eventType;
    private int successfulCount;
    private int failedCount;
    private int rolledBackCount;
    private int errorCount;
    private LocalDateTime timestamp;
}