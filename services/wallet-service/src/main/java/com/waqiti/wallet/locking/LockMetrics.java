package com.waqiti.wallet.locking;

import lombok.Builder;
import lombok.Data;

/**
 * Metrics for distributed wallet locking operations
 *
 * Used for monitoring lock performance and detecting contention issues.
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-01-16
 */
@Data
@Builder
public class LockMetrics {
    private double successfulAcquisitions;
    private double failedAcquisitions;
    private double timeouts;
    private double contentions;
    private double averageWaitTime;
    private double maxWaitTime;
    private double totalWaitTime;

    /**
     * Calculate lock acquisition success rate
     */
    public double getSuccessRate() {
        double total = successfulAcquisitions + failedAcquisitions;
        return total > 0 ? (successfulAcquisitions / total) * 100.0 : 0.0;
    }

    /**
     * Calculate lock contention rate
     */
    public double getContentionRate() {
        return successfulAcquisitions > 0 ?
            (contentions / successfulAcquisitions) * 100.0 : 0.0;
    }
}
