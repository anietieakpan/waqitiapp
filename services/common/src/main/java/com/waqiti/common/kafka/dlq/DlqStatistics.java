package com.waqiti.common.kafka.dlq;

import com.waqiti.common.kafka.dlq.entity.DlqRecordEntity;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * DLQ Statistics for monitoring and alerting
 */
@Data
@Builder
public class DlqStatistics {

    private long totalMessages;
    private long pendingMessages;
    private long parkedMessages;
    private long reprocessedMessages;

    private Map<DlqEventType, Long> messagesByEventType;

    private Optional<DlqRecordEntity> oldestPendingMessage;

    private long criticalFailureCount;

    private LocalDateTime timestamp;

    /**
     * Calculate health score (0-100)
     * 100 = Perfect (no pending/parked messages)
     * 0 = Critical (many failed messages)
     */
    public int getHealthScore() {
        if (totalMessages == 0) {
            return 100;
        }

        long problematicMessages = pendingMessages + parkedMessages;
        double ratio = (double) problematicMessages / totalMessages;

        // Health decreases as ratio increases
        int score = (int) (100 * (1 - ratio));

        // Penalize critical failures
        score -= (int) (criticalFailureCount * 5);

        return Math.max(0, Math.min(100, score));
    }

    /**
     * Determine if system requires immediate attention
     */
    public boolean requiresImmediateAttention() {
        return criticalFailureCount > 10 ||
               parkedMessages > 100 ||
               pendingMessages > 1000 ||
               getHealthScore() < 50;
    }
}
