package com.waqiti.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Transaction Summary DTO
 *
 * Aggregated transaction data for analytics and reporting.
 * Used by AdvancedTransactionAnalyticsService to provide
 * comprehensive transaction insights.
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSummary {

    // User/Account Identifiers
    private Long userId;
    private Long accountId;
    private String accountNumber;

    // Financial Aggregates
    private BigDecimal totalSpent;
    private BigDecimal totalIncome;
    private BigDecimal netCashFlow;
    private BigDecimal averageTransactionAmount;
    private BigDecimal largestTransaction;
    private BigDecimal smallestTransaction;

    // Transaction Counts
    private Integer totalTransactionCount;
    private Integer incomingTransactionCount;
    private Integer outgoingTransactionCount;
    private Integer declinedTransactionCount;

    // Date Range
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer dayCount;

    // Category Breakdown
    private Map<String, BigDecimal> categorySpending;
    private Map<String, Integer> categoryTransactionCount;
    private String topSpendingCategory;
    private BigDecimal topCategoryAmount;

    // Merchant Breakdown
    private Map<String, BigDecimal> merchantSpending;
    private Map<String, Integer> merchantTransactionCount;
    private String topMerchant;
    private BigDecimal topMerchantAmount;

    // Payment Method Breakdown
    private Map<String, BigDecimal> paymentMethodBreakdown;
    private Map<String, Integer> paymentMethodCount;

    // Time-based Analytics
    private Map<String, BigDecimal> dailySpending;
    private Map<String, BigDecimal> weeklySpending;
    private Map<String, BigDecimal> monthlySpending;

    // Behavioral Insights
    private BigDecimal averageDailySpending;
    private BigDecimal averageWeeklySpending;
    private BigDecimal averageMonthlySpending;
    private Integer peakSpendingDayOfWeek; // 1=Monday, 7=Sunday
    private Integer peakSpendingHourOfDay; // 0-23

    // Comparison Metrics (vs previous period)
    private BigDecimal spendingChangePct;
    private BigDecimal incomeChangePct;
    private Integer transactionCountChangePct;

    // Metadata
    private LocalDateTime generatedAt;
    private String reportPeriod; // "DAILY", "WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY"
    private String currency;
    private String timezone;

    // Flags
    private Boolean hasAnomalies;
    private Boolean hasLargeTransactions;
    private Boolean hasRecurringPayments;
    private Integer recurringPaymentCount;

    // Helper Methods
    public boolean isNetPositive() {
        return netCashFlow != null && netCashFlow.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean hasSpendingIncreased() {
        return spendingChangePct != null && spendingChangePct.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean hasIncomeIncreased() {
        return incomeChangePct != null && incomeChangePct.compareTo(BigDecimal.ZERO) > 0;
    }

    public BigDecimal getAverageTransactionSize() {
        if (totalTransactionCount == null || totalTransactionCount == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = (totalSpent != null ? totalSpent : BigDecimal.ZERO)
                .add(totalIncome != null ? totalIncome : BigDecimal.ZERO);
        return total.divide(BigDecimal.valueOf(totalTransactionCount), 2, RoundingMode.HALF_UP);
    }
}
