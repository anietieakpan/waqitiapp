package com.waqiti.analytics.dto.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

/**
 * Monthly Spending Data Transfer Object
 * 
 * Represents monthly spending analytics with comprehensive budget comparison and analysis.
 * This DTO follows Domain-Driven Design principles and includes comprehensive
 * validation, month-over-month comparison, and budget tracking capabilities.
 * 
 * <p>Used for:
 * <ul>
 *   <li>Monthly spending pattern analysis</li>
 *   <li>Month-over-month spending comparison</li>
 *   <li>Budget tracking and variance analysis</li>
 *   <li>Quarterly and yearly aggregation base data</li>
 *   <li>Seasonal spending trend identification</li>
 *   <li>Financial planning and forecasting</li>
 * </ul>
 * 
 * <p>Version: 1.0
 * <p>API Version: v1
 * 
 * @author Waqiti Analytics Team
 * @since 1.0.0
 * @version 1.0
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"monthlyMetadata"})
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(
    name = "MonthlySpending",
    description = "Monthly spending analytics with budget comparison and comprehensive analysis",
    example = """
        {
          "year": 2024,
          "month": 1,
          "monthStartDate": "2024-01-01",
          "monthEndDate": "2024-01-31",
          "amount": 3850.75,
          "transactionCount": 185,
          "averagePerTransaction": 20.82,
          "averagePerDay": 124.22,
          "monthOverMonthChange": 12.5,
          "budgetAmount": 4000.00,
          "budgetVariance": -3.7,
          "isOverBudget": false,
          "currency": "USD",
          "version": "1.0"
        }
        """
)
public class MonthlySpending implements Serializable, Comparable<MonthlySpending> {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Year for this monthly spending
     */
    @NotNull(message = "Year cannot be null")
    @Positive(message = "Year must be positive")
    @JsonProperty("year")
    @JsonPropertyDescription("Year for this monthly spending")
    @Schema(
        description = "Year for this monthly spending",
        example = "2024",
        required = true
    )
    private Integer year;

    /**
     * Month (1-12)
     */
    @NotNull(message = "Month cannot be null")
    @Min(value = 1, message = "Month must be between 1 and 12")
    @Max(value = 12, message = "Month must be between 1 and 12")
    @JsonProperty("month")
    @JsonPropertyDescription("Month number (1-12)")
    @Schema(
        description = "Month number",
        example = "1",
        minimum = "1",
        maximum = "12",
        required = true
    )
    private Integer month;

    /**
     * First day of the month
     */
    @NotNull(message = "Month start date cannot be null")
    @JsonProperty("monthStartDate")
    @JsonPropertyDescription("First day of the month")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @Schema(
        description = "First day of the month",
        example = "2024-01-01",
        format = "date",
        required = true
    )
    private LocalDate monthStartDate;

    /**
     * Last day of the month
     */
    @NotNull(message = "Month end date cannot be null")
    @JsonProperty("monthEndDate")
    @JsonPropertyDescription("Last day of the month")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @Schema(
        description = "Last day of the month",
        example = "2024-01-31",
        format = "date",
        required = true
    )
    private LocalDate monthEndDate;

    /**
     * Total spending amount for this month
     */
    @NotNull(message = "Amount cannot be null")
    @DecimalMin(value = "0.00", message = "Amount must be non-negative")
    @JsonProperty("amount")
    @JsonPropertyDescription("Total spending amount for this month")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Total spending amount for this month",
        example = "3850.75",
        minimum = "0",
        required = true
    )
    private BigDecimal amount;

    /**
     * Number of transactions in this month
     */
    @Min(value = 0, message = "Transaction count must be non-negative")
    @JsonProperty("transactionCount")
    @JsonPropertyDescription("Number of transactions in this month")
    @Schema(
        description = "Number of transactions in this month",
        example = "185",
        minimum = "0",
        required = true
    )
    private long transactionCount;

    /**
     * Average amount per transaction for this month
     */
    @DecimalMin(value = "0.00", message = "Average per transaction must be non-negative")
    @JsonProperty("averagePerTransaction")
    @JsonPropertyDescription("Average amount per transaction for this month")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Average amount per transaction for this month",
        example = "20.82",
        minimum = "0"
    )
    private BigDecimal averagePerTransaction;

    /**
     * Average daily spending for this month
     */
    @DecimalMin(value = "0.00", message = "Average per day must be non-negative")
    @JsonProperty("averagePerDay")
    @JsonPropertyDescription("Average daily spending for this month")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Average daily spending for this month",
        example = "124.22",
        minimum = "0"
    )
    private BigDecimal averagePerDay;

    /**
     * Month-over-month percentage change
     */
    @JsonProperty("monthOverMonthChange")
    @JsonPropertyDescription("Month-over-month percentage change (positive for increase, negative for decrease)")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Month-over-month percentage change",
        example = "12.5"
    )
    private BigDecimal monthOverMonthChange;

    /**
     * Budget amount for this month
     */
    @DecimalMin(value = "0.00", message = "Budget amount must be non-negative")
    @JsonProperty("budgetAmount")
    @JsonPropertyDescription("Budget amount allocated for this month")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Budget amount allocated for this month",
        example = "4000.00",
        minimum = "0"
    )
    private BigDecimal budgetAmount;

    /**
     * Budget variance percentage
     */
    @JsonProperty("budgetVariance")
    @JsonPropertyDescription("Budget variance percentage (positive for over budget, negative for under budget)")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Budget variance percentage",
        example = "-3.7"
    )
    private BigDecimal budgetVariance;

    /**
     * Whether spending exceeded the budget
     */
    @JsonProperty("isOverBudget")
    @JsonPropertyDescription("Whether spending exceeded the budget for this month")
    @Schema(
        description = "Whether spending exceeded the budget",
        example = "false"
    )
    private Boolean isOverBudget;

    /**
     * Remaining budget amount
     */
    @JsonProperty("budgetRemaining")
    @JsonPropertyDescription("Remaining budget amount for this month")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Remaining budget amount",
        example = "149.25"
    )
    private BigDecimal budgetRemaining;

    /**
     * Spending during first half of month
     */
    @DecimalMin(value = "0.00", message = "First half spending must be non-negative")
    @JsonProperty("firstHalfSpending")
    @JsonPropertyDescription("Spending during first half of the month")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Spending during first half of the month",
        example = "1825.30",
        minimum = "0"
    )
    private BigDecimal firstHalfSpending;

    /**
     * Spending during second half of month
     */
    @DecimalMin(value = "0.00", message = "Second half spending must be non-negative")
    @JsonProperty("secondHalfSpending")
    @JsonPropertyDescription("Spending during second half of the month")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Spending during second half of the month",
        example = "2025.45",
        minimum = "0"
    )
    private BigDecimal secondHalfSpending;

    /**
     * Year-to-date spending total
     */
    @DecimalMin(value = "0.00", message = "YTD spending must be non-negative")
    @JsonProperty("yearToDateSpending")
    @JsonPropertyDescription("Year-to-date spending total including this month")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Year-to-date spending total",
        example = "3850.75",
        minimum = "0"
    )
    private BigDecimal yearToDateSpending;

    /**
     * Rolling 12-month average
     */
    @DecimalMin(value = "0.00", message = "Rolling average must be non-negative")
    @JsonProperty("rolling12MonthAverage")
    @JsonPropertyDescription("Rolling 12-month average spending amount")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Rolling 12-month average spending amount",
        example = "3650.80",
        minimum = "0"
    )
    private BigDecimal rolling12MonthAverage;

    /**
     * Currency code for the amounts
     */
    @JsonProperty("currency")
    @JsonPropertyDescription("ISO 4217 currency code")
    @Schema(
        description = "ISO 4217 currency code",
        example = "USD",
        pattern = "^[A-Z]{3}$"
    )
    private String currency;

    /**
     * DTO version for API evolution support
     */
    @JsonProperty("version")
    @JsonPropertyDescription("DTO version for API compatibility")
    @Schema(
        description = "DTO version for API compatibility",
        example = "1.0",
        defaultValue = "1.0"
    )
    @Builder.Default
    private String version = "1.0";

    /**
     * Monthly metadata for additional analysis
     */
    @JsonProperty("monthlyMetadata")
    @JsonPropertyDescription("Additional metadata for monthly analysis")
    @Schema(description = "Additional metadata for monthly spending analysis")
    private MonthlyMetadata monthlyMetadata;

    /**
     * Calculates the average per transaction with defensive programming
     * 
     * @return calculated average or zero if no transactions
     */
    public BigDecimal calculateAveragePerTransaction() {
        if (transactionCount == 0 || amount == null) {
            return BigDecimal.ZERO;
        }
        return amount.divide(BigDecimal.valueOf(transactionCount), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates the average per day with defensive programming
     * 
     * @return calculated daily average
     */
    public BigDecimal calculateAveragePerDay() {
        if (amount == null || monthStartDate == null || monthEndDate == null) {
            return BigDecimal.ZERO;
        }
        long daysInMonth = monthStartDate.until(monthEndDate).getDays() + 1;
        return amount.divide(BigDecimal.valueOf(daysInMonth), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates budget variance percentage
     * 
     * @return budget variance percentage
     */
    public BigDecimal calculateBudgetVariance() {
        if (budgetAmount == null || amount == null || budgetAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal variance = amount.subtract(budgetAmount);
        return variance.divide(budgetAmount, 4, RoundingMode.HALF_UP)
                      .multiply(BigDecimal.valueOf(100))
                      .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates remaining budget amount
     * 
     * @return remaining budget or zero if over budget
     */
    public BigDecimal calculateBudgetRemaining() {
        if (budgetAmount == null || amount == null) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal remaining = budgetAmount.subtract(amount);
        return remaining.max(BigDecimal.ZERO);
    }

    /**
     * Determines if this month is over budget
     * 
     * @return true if spending exceeds budget
     */
    public boolean calculateIsOverBudget() {
        return budgetAmount != null && amount != null && amount.compareTo(budgetAmount) > 0;
    }

    /**
     * Gets the spending velocity (how quickly budget is being consumed)
     * 
     * @param currentDay current day of the month
     * @return spending velocity ratio
     */
    public BigDecimal getSpendingVelocity(int currentDay) {
        if (budgetAmount == null || amount == null || budgetAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        int daysInMonth = getDaysInMonth();
        BigDecimal expectedSpending = budgetAmount.multiply(BigDecimal.valueOf((double) currentDay / daysInMonth));
        
        if (expectedSpending.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return amount.divide(expectedSpending, 2, RoundingMode.HALF_UP);
    }

    /**
     * Gets the number of days in this month
     * 
     * @return number of days in the month
     */
    public int getDaysInMonth() {
        if (year == null || month == null) {
            return 30; // Default assumption
        }
        return YearMonth.of(year, month).lengthOfMonth();
    }

    /**
     * Gets the quarter for this month
     * 
     * @return quarter number (1-4)
     */
    public int getQuarter() {
        if (month == null) {
            return 1;
        }
        return (month - 1) / 3 + 1;
    }

    /**
     * Gets the season for this month
     * 
     * @return season name
     */
    public String getSeason() {
        if (month == null) {
            return "UNKNOWN";
        }
        
        return switch (month) {
            case 12, 1, 2 -> "WINTER";
            case 3, 4, 5 -> "SPRING";
            case 6, 7, 8 -> "SUMMER";
            case 9, 10, 11 -> "FALL";
            default -> "UNKNOWN";
        };
    }

    /**
     * Calculates first vs second half spending ratio
     * 
     * @return ratio of first half to second half spending
     */
    public BigDecimal calculateFirstToSecondHalfRatio() {
        if (secondHalfSpending == null || secondHalfSpending.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if (firstHalfSpending == null) {
            return BigDecimal.ZERO;
        }
        return firstHalfSpending.divide(secondHalfSpending, 2, RoundingMode.HALF_UP);
    }

    /**
     * Validates the business rules for this monthly spending data
     * 
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return year != null && year > 0
            && month != null && month >= 1 && month <= 12
            && monthStartDate != null && monthEndDate != null
            && amount != null && amount.compareTo(BigDecimal.ZERO) >= 0
            && monthStartDate.isBefore(monthEndDate)
            && monthStartDate.getDayOfMonth() == 1
            && monthEndDate.equals(monthStartDate.with(TemporalAdjusters.lastDayOfMonth()));
    }

    /**
     * Creates a MonthlySpending from year and month
     * 
     * @param year the year
     * @param month the month (1-12)
     * @return new MonthlySpending with calculated dates
     */
    public static MonthlySpending fromYearMonth(int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        
        return MonthlySpending.builder()
            .year(year)
            .month(month)
            .monthStartDate(startDate)
            .monthEndDate(endDate)
            .amount(BigDecimal.ZERO)
            .transactionCount(0)
            .build();
    }

    /**
     * Creates a sanitized copy suitable for external APIs
     * 
     * @return sanitized copy without internal metadata
     */
    public MonthlySpending sanitizedCopy() {
        return this.toBuilder()
            .monthlyMetadata(null)
            .build();
    }

    /**
     * Natural ordering by year and month
     */
    @Override
    public int compareTo(MonthlySpending other) {
        if (other == null) {
            return 1;
        }
        
        // Compare by year first
        if (this.year != null && other.year != null) {
            int yearComparison = this.year.compareTo(other.year);
            if (yearComparison != 0) {
                return yearComparison;
            }
        }
        
        // Then compare by month
        if (this.month != null && other.month != null) {
            return this.month.compareTo(other.month);
        }
        
        return 0;
    }

    /**
     * Monthly metadata inner class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Additional metadata for monthly spending analysis")
    public static class MonthlyMetadata implements Serializable {
        
        @Serial
        private static final long serialVersionUID = 1L;

        @JsonProperty("hasHolidays")
        @Schema(description = "Whether this month contains major holidays", example = "true")
        private Boolean hasHolidays;

        @JsonProperty("holidayCount")
        @Schema(description = "Number of holidays in this month", example = "2")
        private Integer holidayCount;

        @JsonProperty("businessDaysCount")
        @Schema(description = "Number of business days in this month", example = "21")
        private Integer businessDaysCount;

        @JsonProperty("seasonalFactor")
        @Schema(description = "Seasonal adjustment factor for spending", example = "1.15")
        private BigDecimal seasonalFactor;

        @JsonProperty("economicIndicator")
        @Schema(description = "Economic indicator for the month", example = "STABLE")
        private String economicIndicator;

        @JsonProperty("dataCompleteness")
        @Schema(description = "Data completeness score", example = "0.98")
        private BigDecimal dataCompleteness;

        @JsonProperty("forecastAccuracy")
        @Schema(description = "Forecast accuracy if this is a forecasted month", example = "0.92")
        private BigDecimal forecastAccuracy;

        @JsonProperty("aggregationSource")
        @Schema(description = "Source of monthly aggregation", example = "WEEKLY_ROLLUP")
        private String aggregationSource;

        @JsonProperty("lastUpdated")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        @Schema(description = "Last update timestamp", format = "date-time")
        private java.time.Instant lastUpdated;
    }

    /**
     * Custom equals method for better performance
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MonthlySpending that)) return false;
        return Objects.equals(year, that.year) && 
               Objects.equals(month, that.month);
    }

    /**
     * Custom hashCode method for better performance
     */
    @Override
    public int hashCode() {
        return Objects.hash(year, month);
    }
}