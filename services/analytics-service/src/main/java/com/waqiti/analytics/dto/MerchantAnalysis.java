package com.waqiti.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Merchant Analysis DTO
 *
 * Detailed analysis of spending patterns by merchant.
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantAnalysis {

    // Merchant Details
    private String merchantName;
    private String merchantId;
    private String merchantCategory;
    private String merchantCategoryCode; // MCC

    // Spending Metrics
    private BigDecimal totalSpending;
    private BigDecimal averageTransactionAmount;
    private Integer transactionCount;
    private BigDecimal percentageOfTotalSpending;

    // Period
    private LocalDate startDate;
    private LocalDate endDate;
    private String period;

    // Frequency
    private String frequency; // "DAILY", "WEEKLY", "MONTHLY", "OCCASIONAL"
    private Integer averageDaysBetweenTransactions;
    private LocalDate firstTransactionDate;
    private LocalDate lastTransactionDate;
    private Integer daysSinceLastTransaction;

    // Patterns
    private Boolean isRecurring;
    private Boolean isSubscription;
    private BigDecimal predictedNextAmount;
    private LocalDate predictedNextDate;

    // Trends
    private String trend; // "INCREASING", "DECREASING", "STABLE"
    private BigDecimal trendPercentage;
    private BigDecimal previousPeriodSpending;
    private BigDecimal spendingChange;
    private BigDecimal spendingChangePercentage;

    // Transaction Size Distribution
    private BigDecimal smallestTransaction;
    private BigDecimal largestTransaction;
    private BigDecimal medianTransaction;
    private BigDecimal standardDeviation;

    // Ranking
    private Integer rank; // 1 = highest spending merchant
    private BigDecimal concentrationRatio; // % of spending at this merchant

    // Flags
    private Boolean hasRecentlyIncreased;
    private Boolean hasUnusualActivity;
    private Boolean requiresReview;

    // Payment Methods
    private List<String> paymentMethodsUsed;
    private String primaryPaymentMethod;

    // Time Patterns
    private Integer mostCommonHour; // 0-23
    private Integer mostCommonDayOfWeek; // 1-7
    private String timePattern; // "MORNING", "AFTERNOON", "EVENING", "NIGHT"

    // Metadata
    private String currency;
    private LocalDateTime generatedAt;
    private String notes;

    // Helper Methods
    public boolean isTopMerchant() {
        return rank != null && rank <= 5;
    }

    public boolean isSubscriptionLikely() {
        return isRecurring != null && isRecurring &&
                standardDeviation != null && standardDeviation.compareTo(BigDecimal.valueOf(5)) < 0;
    }

    public boolean isHighConcentration() {
        return concentrationRatio != null && concentrationRatio.compareTo(BigDecimal.valueOf(20)) > 0;
    }

    public boolean isIncreasingSpending() {
        return "INCREASING".equals(trend);
    }

    public Integer getDaysSinceLastVisit() {
        if (lastTransactionDate == null) return null;
        return (int) java.time.temporal.ChronoUnit.DAYS.between(lastTransactionDate, LocalDate.now());
    }
}
