package com.waqiti.compliance.fincen;

import com.waqiti.compliance.fincen.entity.ManualFilingQueueEntry;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Manual Filing Queue Statistics
 *
 * @author Waqiti Compliance Team
 * @version 2.0.0
 * @since 2025-11-04
 */
@Data
@Builder
public class ManualFilingQueueStatistics {

    private long totalPending;
    private long totalOverdue;
    private long totalFiled;
    private long expeditedPending;

    private Optional<ManualFilingQueueEntry> oldestPendingEntry;

    private Double averageTimeToFile; // In seconds

    private LocalDateTime timestamp;

    /**
     * Calculate health score (0-100)
     */
    public int getHealthScore() {
        // Perfect score if no pending or overdue
        if (totalPending == 0 && totalOverdue == 0) {
            return 100;
        }

        int score = 100;

        // Penalize for overdue filings (critical)
        score -= (int) (totalOverdue * 20);

        // Penalize for high pending count
        if (totalPending > 10) {
            score -= 10;
        } else if (totalPending > 5) {
            score -= 5;
        }

        // Penalize for expedited pending
        score -= (int) (expeditedPending * 5);

        return Math.max(0, Math.min(100, score));
    }

    /**
     * Determine if immediate attention required
     */
    public boolean requiresImmediateAttention() {
        return totalOverdue > 0 || expeditedPending > 3 || getHealthScore() < 50;
    }
}
