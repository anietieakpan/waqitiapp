package com.waqiti.analytics.dto.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

/**
 * Weekly Spending Data Transfer Object
 * 
 * Represents weekly spending analytics with ISO week support for financial analysis.
 * This DTO follows Domain-Driven Design principles and includes comprehensive
 * validation, week-over-week comparison, and ISO week standard compliance.
 * 
 * <p>Used for:
 * <ul>
 *   <li>Weekly spending pattern analysis</li>
 *   <li>Week-over-week spending comparison</li>
 *   <li>Monthly aggregation base data</li>
 *   <li>Budget tracking and variance analysis</li>
 *   <li>Seasonal spending trend identification</li>
 *   <li>ISO week standard reporting</li>
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
@ToString(exclude = {"weeklyMetadata"})
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(
    name = "WeeklySpending",
    description = "Weekly spending analytics with ISO week support and comprehensive comparison metrics",
    example = """
        {
          "weekStartDate": "2024-01-15",
          "weekEndDate": "2024-01-21",
          "isoWeek": 3,
          "isoYear": 2024,
          "amount": 892.50,
          "transactionCount": 45,
          "averagePerTransaction": 19.83,
          "averagePerDay": 127.50,
          "weekOverWeekChange": 8.2,
          "weekdaySpending": 650.00,
          "weekendSpending": 242.50,
          "currency": "USD",
          "version": "1.0"
        }
        """
)
public class WeeklySpending implements Serializable, Comparable<WeeklySpending> {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Start date of the week (Monday in ISO week)
     */
    @NotNull(message = "Week start date cannot be null")
    @JsonProperty("weekStartDate")
    @JsonPropertyDescription("Start date of the week (Monday in ISO week standard)")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @Schema(
        description = "Start date of the week (Monday)",
        example = "2024-01-15",
        format = "date",
        required = true
    )
    private LocalDate weekStartDate;

    /**
     * End date of the week (Sunday in ISO week)
     */
    @NotNull(message = "Week end date cannot be null")
    @JsonProperty("weekEndDate")
    @JsonPropertyDescription("End date of the week (Sunday in ISO week standard)")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @Schema(
        description = "End date of the week (Sunday)",
        example = "2024-01-21",
        format = "date",
        required = true
    )
    private LocalDate weekEndDate;

    /**
     * ISO week number (1-53)
     */
    @NotNull(message = "ISO week cannot be null")
    @Min(value = 1, message = "ISO week must be between 1 and 53")
    @JsonProperty("isoWeek")
    @JsonPropertyDescription("ISO week number (1-53)")
    @Schema(
        description = "ISO week number",
        example = "3",
        minimum = "1",
        maximum = "53",
        required = true
    )
    private Integer isoWeek;

    /**
     * ISO year for the week
     */
    @NotNull(message = "ISO year cannot be null")
    @Positive(message = "ISO year must be positive")
    @JsonProperty("isoYear")
    @JsonPropertyDescription("ISO year for the week")
    @Schema(
        description = "ISO year for the week",
        example = "2024",
        required = true
    )
    private Integer isoYear;

    /**
     * Total spending amount for this week
     */
    @NotNull(message = "Amount cannot be null")
    @DecimalMin(value = "0.00", message = "Amount must be non-negative")
    @JsonProperty("amount")
    @JsonPropertyDescription("Total spending amount for this week")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Total spending amount for this week",
        example = "892.50",
        minimum = "0",
        required = true
    )
    private BigDecimal amount;

    /**
     * Number of transactions in this week
     */
    @Min(value = 0, message = "Transaction count must be non-negative")
    @JsonProperty("transactionCount")
    @JsonPropertyDescription("Number of transactions in this week")
    @Schema(
        description = "Number of transactions in this week",
        example = "45",
        minimum = "0",
        required = true
    )
    private long transactionCount;

    /**
     * Average amount per transaction for this week
     */
    @DecimalMin(value = "0.00", message = "Average per transaction must be non-negative")
    @JsonProperty("averagePerTransaction")
    @JsonPropertyDescription("Average amount per transaction for this week")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Average amount per transaction for this week",
        example = "19.83",
        minimum = "0"
    )
    private BigDecimal averagePerTransaction;

    /**
     * Average daily spending for this week
     */
    @DecimalMin(value = "0.00", message = "Average per day must be non-negative")
    @JsonProperty("averagePerDay")
    @JsonPropertyDescription("Average daily spending for this week")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Average daily spending for this week",
        example = "127.50",
        minimum = "0"
    )
    private BigDecimal averagePerDay;

    /**
     * Week-over-week percentage change
     */
    @JsonProperty("weekOverWeekChange")
    @JsonPropertyDescription("Week-over-week percentage change (positive for increase, negative for decrease)")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Week-over-week percentage change",
        example = "8.2"
    )
    private BigDecimal weekOverWeekChange;

    /**
     * Spending during weekdays (Monday-Friday)
     */
    @DecimalMin(value = "0.00", message = "Weekday spending must be non-negative")
    @JsonProperty("weekdaySpending")
    @JsonPropertyDescription("Total spending during weekdays (Monday-Friday)")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Total spending during weekdays",
        example = "650.00",
        minimum = "0"
    )
    private BigDecimal weekdaySpending;

    /**
     * Spending during weekend (Saturday-Sunday)
     */
    @DecimalMin(value = "0.00", message = "Weekend spending must be non-negative")
    @JsonProperty("weekendSpending")
    @JsonPropertyDescription("Total spending during weekend (Saturday-Sunday)")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Total spending during weekend",
        example = "242.50",
        minimum = "0"
    )
    private BigDecimal weekendSpending;

    /**
     * Highest spending day of the week
     */
    @JsonProperty("peakSpendingDay")
    @JsonPropertyDescription("Day of the week with highest spending")
    @Schema(
        description = "Day of the week with highest spending",
        example = "FRIDAY",
        allowableValues = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"}
    )
    private DayOfWeek peakSpendingDay;

    /**
     * Amount spent on the peak spending day
     */
    @DecimalMin(value = "0.00", message = "Peak day amount must be non-negative")
    @JsonProperty("peakSpendingDayAmount")
    @JsonPropertyDescription("Amount spent on the peak spending day")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Amount spent on the peak spending day",
        example = "185.75",
        minimum = "0"
    )
    private BigDecimal peakSpendingDayAmount;

    /**
     * Rolling 4-week average for comparison
     */
    @DecimalMin(value = "0.00", message = "Rolling average must be non-negative")
    @JsonProperty("rolling4WeekAverage")
    @JsonPropertyDescription("Rolling 4-week average spending amount")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Rolling 4-week average spending amount",
        example = "825.30",
        minimum = "0"
    )
    private BigDecimal rolling4WeekAverage;

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
     * Weekly metadata for additional analysis
     */
    @JsonProperty("weeklyMetadata")
    @JsonPropertyDescription("Additional metadata for weekly analysis")
    @Schema(description = "Additional metadata for weekly spending analysis")
    private WeeklyMetadata weeklyMetadata;

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
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        return amount.divide(BigDecimal.valueOf(7), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates the weekday to weekend spending ratio
     * 
     * @return ratio of weekday to weekend spending
     */
    public BigDecimal calculateWeekdayToWeekendRatio() {
        if (weekendSpending == null || weekendSpending.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if (weekdaySpending == null) {
            return BigDecimal.ZERO;
        }
        return weekdaySpending.divide(weekendSpending, 2, RoundingMode.HALF_UP);
    }

    /**
     * Determines if this is a high spending week based on threshold
     * 
     * @param threshold the threshold amount for high spending
     * @return true if spending exceeds threshold
     */
    public boolean isHighSpendingWeek(BigDecimal threshold) {
        return amount != null && threshold != null && amount.compareTo(threshold) > 0;
    }

    /**
     * Calculates spending variance from rolling average
     * 
     * @return variance percentage from rolling average
     */
    public BigDecimal calculateVarianceFromRollingAverage() {
        if (rolling4WeekAverage == null || amount == null || rolling4WeekAverage.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal variance = amount.subtract(rolling4WeekAverage);
        return variance.divide(rolling4WeekAverage, 4, RoundingMode.HALF_UP)
                      .multiply(BigDecimal.valueOf(100))
                      .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Checks if spending pattern is balanced between weekdays and weekends
     * 
     * @param balanceThreshold tolerance for balance (e.g., 0.2 for 20% tolerance)
     * @return true if spending is relatively balanced
     */
    public boolean isSpendingBalanced(double balanceThreshold) {
        if (weekdaySpending == null || weekendSpending == null || amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }
        
        // Expected: weekdays = 5/7, weekends = 2/7 of total
        BigDecimal expectedWeekdayRatio = BigDecimal.valueOf(5.0 / 7.0);
        BigDecimal actualWeekdayRatio = weekdaySpending.divide(amount, 4, RoundingMode.HALF_UP);
        
        BigDecimal difference = expectedWeekdayRatio.subtract(actualWeekdayRatio).abs();
        return difference.compareTo(BigDecimal.valueOf(balanceThreshold)) <= 0;
    }

    /**
     * Gets the quarter for this week
     * 
     * @return quarter number (1-4)
     */
    public int getQuarter() {
        if (weekStartDate == null) {
            return 1;
        }
        return (weekStartDate.getMonthValue() - 1) / 3 + 1;
    }

    /**
     * Validates the business rules for this weekly spending data
     * 
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return weekStartDate != null && weekEndDate != null
            && isoWeek != null && isoWeek >= 1 && isoWeek <= 53
            && isoYear != null && isoYear > 0
            && amount != null && amount.compareTo(BigDecimal.ZERO) >= 0
            && weekStartDate.isBefore(weekEndDate)
            && weekStartDate.getDayOfWeek() == DayOfWeek.MONDAY
            && weekEndDate.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    /**
     * Creates a WeeklySpending from a start date
     * 
     * @param startDate the Monday of the week
     * @return new WeeklySpending with calculated dates and ISO week
     */
    public static WeeklySpending fromStartDate(LocalDate startDate) {
        LocalDate weekStart = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        
        return WeeklySpending.builder()
            .weekStartDate(weekStart)
            .weekEndDate(weekEnd)
            .isoWeek(weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
            .isoYear(weekStart.get(IsoFields.WEEK_BASED_YEAR))
            .amount(BigDecimal.ZERO)
            .transactionCount(0)
            .build();
    }

    /**
     * Creates a sanitized copy suitable for external APIs
     * 
     * @return sanitized copy without internal metadata
     */
    public WeeklySpending sanitizedCopy() {
        return this.toBuilder()
            .weeklyMetadata(null)
            .build();
    }

    /**
     * Natural ordering by ISO year and week
     */
    @Override
    public int compareTo(WeeklySpending other) {
        if (other == null) {
            return 1;
        }
        
        // Compare by ISO year first
        if (this.isoYear != null && other.isoYear != null) {
            int yearComparison = this.isoYear.compareTo(other.isoYear);
            if (yearComparison != 0) {
                return yearComparison;
            }
        }
        
        // Then compare by ISO week
        if (this.isoWeek != null && other.isoWeek != null) {
            return this.isoWeek.compareTo(other.isoWeek);
        }
        
        // Fallback to start date comparison
        if (this.weekStartDate != null && other.weekStartDate != null) {
            return this.weekStartDate.compareTo(other.weekStartDate);
        }
        
        return 0;
    }

    /**
     * Weekly metadata inner class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Additional metadata for weekly spending analysis")
    public static class WeeklyMetadata implements Serializable {
        
        @Serial
        private static final long serialVersionUID = 1L;

        @JsonProperty("hasHolidays")
        @Schema(description = "Whether this week contains holidays", example = "false")
        private Boolean hasHolidays;

        @JsonProperty("holidayNames")
        @Schema(description = "Names of holidays in this week")
        private String holidayNames;

        @JsonProperty("businessDaysCount")
        @Schema(description = "Number of business days in this week", example = "5")
        private Integer businessDaysCount;

        @JsonProperty("weekType")
        @Schema(description = "Type of week (NORMAL, HOLIDAY, VACATION, etc.)", example = "NORMAL")
        private String weekType;

        @JsonProperty("dataCompleteness")
        @Schema(description = "Data completeness score", example = "1.0")
        private BigDecimal dataCompleteness;

        @JsonProperty("aggregationSource")
        @Schema(description = "Source of weekly aggregation", example = "DAILY_ROLLUP")
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
        if (!(o instanceof WeeklySpending that)) return false;
        return Objects.equals(isoWeek, that.isoWeek) && 
               Objects.equals(isoYear, that.isoYear) &&
               Objects.equals(weekStartDate, that.weekStartDate);
    }

    /**
     * Custom hashCode method for better performance
     */
    @Override
    public int hashCode() {
        return Objects.hash(isoWeek, isoYear, weekStartDate);
    }
}