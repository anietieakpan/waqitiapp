package com.waqiti.common.kafka.dlq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Statistics for DLQ recovery monitoring.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DLQRecoveryStats {
    private String topic;
    private long recoveredCount;
    private long manualInterventionCount;
    private long pendingRetryCount;
    private double recoverySuccessRate;
}
