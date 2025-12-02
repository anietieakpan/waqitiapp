package com.waqiti.payment.idempotency;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Statistics about idempotency records for monitoring
 */
@Data
@Builder
public class IdempotencyStatistics {
    private long totalRecords;
    private long processingRecords;
    private long completedRecords;
    private long failedRecords;
    private LocalDateTime timestamp;
}
