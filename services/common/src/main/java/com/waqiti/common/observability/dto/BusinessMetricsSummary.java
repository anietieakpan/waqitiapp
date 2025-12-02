package com.waqiti.common.observability.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.List;

/**
 * Business metrics summary for observability dashboard
 */
@Data
@Builder
public class BusinessMetricsSummary {
    private final long totalTransactions;
    private final double revenue;
    private final long activeUsers;
    private final double conversionRate;
    private final double averageTransactionValue;
    private final long failedTransactions;
    private final Instant timestamp;
    private final List<BusinessTrend> trends;
    private final long pendingTransactions;
    private final long completedTransactions;
    private final double successRate;
    private final double totalPlatformBalance;
    private final double totalPaymentTransactions;
    private final double totalUserRegistrations;
    private final double totalFraudDetections;
    
    @Data
    @Builder
    public static class BusinessTrend {
        private final String metric;
        private final double value;
        private final double changePercent;
        private final String trend; // "up", "down", "stable"
    }
}