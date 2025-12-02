package com.waqiti.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Category Behavior DTO
 *
 * Behavioral patterns and insights for a spending category.
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryBehavior {

    // Category Details
    private String categoryCode;
    private String categoryName;
    private String categoryType; // "ESSENTIAL", "DISCRETIONARY", "SAVINGS"

    // Spending Patterns
    private BigDecimal averageMonthlySpending;
    private BigDecimal typicalTransactionSize;
    private Integer typicalFrequency; // transactions per month
    private String spendingPattern; // "REGULAR", "IRREGULAR", "SEASONAL", "OCCASIONAL"

    // Timing Patterns
    private Integer mostCommonDayOfWeek; // 1-7
    private Integer mostCommonDayOfMonth; // 1-31
    private Integer mostCommonHour; // 0-23
    private String timePattern; // "MORNING", "AFTERNOON", "EVENING", "NIGHT"

    // Seasonality
    private Boolean hasSeasonalPattern;
    private String peakSeason; // "SPRING", "SUMMER", "FALL", "WINTER"
    private String peakMonth;
    private BigDecimal seasonalityIndex; // 0-2.0 (1.0 = no seasonality)

    // Behavioral Insights
    private String behaviorType; // "IMPULSIVE", "PLANNED", "ROUTINE", "EMERGENCY"
    private BigDecimal planningHorizon; // Average days between similar purchases
    private Boolean isPriceConsistent;
    private BigDecimal priceVariability; // Standard deviation

    // Triggers
    private List<String> commonTriggers; // "WEEKEND", "PAYDAY", "HOLIDAY", "PROMOTIONAL"
    private Boolean increasesOnWeekends;
    private Boolean increasesOnPayday;
    private Boolean respondsToPromotions;

    // Period
    private LocalDate startDate;
    private LocalDate endDate;

    // Trends
    private String trend; // "INCREASING", "DECREASING", "STABLE"
    private BigDecimal trendStrength; // 0-1.0
    private String trendConfidence; // "HIGH", "MEDIUM", "LOW"

    // Predictions
    private BigDecimal predictedNextMonthSpending;
    private Integer predictedNextMonthTransactions;
    private LocalDate predictedNextPurchaseDate;

    // Insights
    private List<CategoryInsight> insights;
    private String primaryInsight;

    // Metadata
    private String currency;
    private Integer dataPointCount; // Number of transactions analyzed
    private Integer confidenceScore; // 0-100

    // Helper Methods
    public boolean isRegularBehavior() {
        return "REGULAR".equals(spendingPattern);
    }

    public boolean isSeasonalCategory() {
        return hasSeasonalPattern != null && hasSeasonalPattern;
    }

    public boolean isImpulsive() {
        return "IMPULSIVE".equals(behaviorType);
    }

    public boolean hasHighConfidence() {
        return confidenceScore != null && confidenceScore > 80;
    }

    public boolean isEssential() {
        return "ESSENTIAL".equals(categoryType);
    }
}
