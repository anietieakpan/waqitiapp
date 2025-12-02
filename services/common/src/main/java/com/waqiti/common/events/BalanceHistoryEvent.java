package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Map;

/**
 * Balance History Event
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceHistoryEvent {

    private String eventId;
    private String accountId;
    private String userId;
    private String transactionId;
    private EventType eventType;
    private BigDecimal previousBalance;
    private BigDecimal currentBalance;
    private BigDecimal balanceChange;
    private String changeReason;
    private LocalDate snapshotDate;
    private MonthlyData monthlyData;
    private TrendData trendData;
    private AnomalyDetails anomalyDetails;
    private String milestoneType;
    private Instant timestamp;
    private String correlationId;

    public enum EventType {
        BALANCE_UPDATED,
        DAILY_BALANCE_SNAPSHOT,
        MONTHLY_BALANCE_SUMMARY,
        BALANCE_TREND_ANALYSIS,
        BALANCE_ANOMALY_DETECTED,
        BALANCE_MILESTONE_REACHED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyData {
        private LocalDate month;
        private BigDecimal openingBalance;
        private BigDecimal closingBalance;
        private BigDecimal averageBalance;
        private BigDecimal highestBalance;
        private BigDecimal lowestBalance;
        private BigDecimal totalDeposits;
        private BigDecimal totalWithdrawals;
        private int transactionCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendData {
        private String trendDirection; // INCREASING, DECREASING, STABLE
        private BigDecimal trendPercentage;
        private int periodDays;
        private BigDecimal averageChange;
        private String confidence; // HIGH, MEDIUM, LOW

        public boolean isNegativeTrend() {
            return "DECREASING".equals(trendDirection);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnomalyDetails {
        private String type; // SUDDEN_DROP, SUDDEN_SPIKE, UNUSUAL_PATTERN
        private String description;
        private BigDecimal expectedValue;
        private BigDecimal actualValue;
        private BigDecimal variance;
        private String severity; // LOW, MEDIUM, HIGH, CRITICAL
        private Map<String, Object> metadata;
    }
}
