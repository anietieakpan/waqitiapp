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
import jakarta.validation.constraints.PastOrPresent;
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
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Daily Spending Data Transfer Object
 * 
 * Represents daily spending analytics for financial behavior analysis.
 * This DTO follows Domain-Driven Design principles and includes comprehensive
 * validation, date-specific calculations, and aggregation helper methods.
 * 
 * <p>Used for:
 * <ul>
 *   <li>Daily spending pattern analysis</li>
 *   <li>Day-over-day spending comparison</li>
 *   <li>Weekly and monthly aggregation base data</li>
 *   <li>Budget tracking and variance analysis</li>
 *   <li>Financial habit identification</li>
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
@ToString(exclude = {"metadata"})
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(
    name = "DailySpending",
    description = "Daily spending analytics with comprehensive metrics and temporal analysis",
    example = """
        {
          "date": "2024-01-15",
          "amount": 127.50,
          "transactionCount": 8,
          "averagePerTransaction": 15.94,
          "dayOfWeek": "MONDAY",
          "isWeekend": false,
          "isBusinessDay": true,
          "currency": "USD",
          "version": "1.0"
        }
        """
)
public class DailySpending implements Serializable, Comparable<DailySpending> {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The date for this spending record
     */
    @NotNull(message = "Date cannot be null")
    @PastOrPresent(message = "Date cannot be in the future")
    @JsonProperty("date")
    @JsonPropertyDescription("The date for this spending record (ISO 8601 format)")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @Schema(
        description = "The date for this spending record",
        example = "2024-01-15",
        format = "date",
        required = true
    )
    private LocalDate date;

    /**
     * Total spending amount for this day
     */
    @NotNull(message = "Amount cannot be null")
    @DecimalMin(value = "0.00", message = "Amount must be non-negative")
    @JsonProperty("amount")
    @JsonPropertyDescription("Total spending amount for this day")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Total spending amount for this day",
        example = "127.50",
        minimum = "0",
        required = true
    )
    private BigDecimal amount;

    /**
     * Number of transactions on this day
     */
    @Min(value = 0, message = "Transaction count must be non-negative")
    @JsonProperty("transactionCount")
    @JsonPropertyDescription("Number of transactions on this day")
    @Schema(
        description = "Number of transactions on this day",
        example = "8",
        minimum = "0",
        required = true
    )
    private long transactionCount;

    /**
     * Average amount per transaction for this day
     */
    @DecimalMin(value = "0.00", message = "Average per transaction must be non-negative")
    @JsonProperty("averagePerTransaction")
    @JsonPropertyDescription("Average amount per transaction for this day")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Average amount per transaction for this day",
        example = "15.94",
        minimum = "0"
    )
    private BigDecimal averagePerTransaction;

    /**
     * Day of the week for temporal analysis
     */
    @JsonProperty("dayOfWeek")
    @JsonPropertyDescription("Day of the week for this spending record")
    @Schema(
        description = "Day of the week",
        example = "MONDAY",
        allowableValues = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"}
    )
    private DayOfWeek dayOfWeek;

    /**
     * Whether this day falls on a weekend
     */
    @JsonProperty("isWeekend")
    @JsonPropertyDescription("Whether this day falls on a weekend")
    @Schema(
        description = "Whether this day falls on a weekend",
        example = "false"
    )
    private Boolean isWeekend;

    /**
     * Whether this day is a business day
     */
    @JsonProperty("isBusinessDay")
    @JsonPropertyDescription("Whether this day is a business day (excludes weekends and holidays)")
    @Schema(
        description = "Whether this day is a business day",
        example = "true"
    )
    private Boolean isBusinessDay;

    /**
     * Day-over-day percentage change
     */
    @JsonProperty("dayOverDayChange")
    @JsonPropertyDescription("Day-over-day percentage change (positive for increase, negative for decrease)")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Day-over-day percentage change",
        example = "12.5"
    )
    private BigDecimal dayOverDayChange;

    /**
     * Rolling 7-day average for this day
     */
    @DecimalMin(value = "0.00", message = "Rolling average must be non-negative")
    @JsonProperty("rolling7DayAverage")
    @JsonPropertyDescription("Rolling 7-day average spending amount")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Rolling 7-day average spending amount",
        example = "115.25",
        minimum = "0"
    )
    private BigDecimal rolling7DayAverage;

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
     * Metadata for additional information
     */
    @JsonProperty("metadata")
    @JsonPropertyDescription("Additional metadata for this spending record")
    @Schema(description = "Additional metadata for tracking and analysis")
    private SpendingMetadata metadata;

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
     * Determines if this is a high spending day based on threshold
     * 
     * @param threshold the threshold amount for high spending
     * @return true if spending exceeds threshold
     */
    public boolean isHighSpendingDay(BigDecimal threshold) {
        return amount != null && threshold != null && amount.compareTo(threshold) > 0;
    }

    /**
     * Calculates the spending variance from a baseline amount
     * 
     * @param baseline the baseline amount to compare against
     * @return variance percentage (positive for above baseline, negative for below)
     */
    public BigDecimal calculateVarianceFromBaseline(BigDecimal baseline) {
        if (baseline == null || amount == null || baseline.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal variance = amount.subtract(baseline);
        return variance.divide(baseline, 4, RoundingMode.HALF_UP)
                      .multiply(BigDecimal.valueOf(100))
                      .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Checks if this day represents unusual spending patterns
     * 
     * @param weekdayAverage average spending for weekdays
     * @param weekendAverage average spending for weekends
     * @param thresholdMultiplier multiplier for anomaly detection (e.g., 2.0 for 200%)
     * @return true if spending is anomalous
     */
    public boolean isAnomalousSpending(BigDecimal weekdayAverage, BigDecimal weekendAverage, double thresholdMultiplier) {
        if (amount == null) {
            return false;
        }
        
        BigDecimal relevantAverage = Boolean.TRUE.equals(isWeekend) ? weekendAverage : weekdayAverage;
        if (relevantAverage == null || relevantAverage.compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }
        
        BigDecimal threshold = relevantAverage.multiply(BigDecimal.valueOf(thresholdMultiplier));
        return amount.compareTo(threshold) > 0;
    }

    /**
     * Gets the day type for categorization
     * 
     * @return string representation of day type
     */
    public String getDayType() {
        if (Boolean.TRUE.equals(isWeekend)) {
            return "WEEKEND";
        } else if (Boolean.TRUE.equals(isBusinessDay)) {
            return "BUSINESS_DAY";
        } else {
            return "HOLIDAY";
        }
    }

    /**
     * Validates the business rules for this daily spending data
     * 
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return date != null
            && amount != null && amount.compareTo(BigDecimal.ZERO) >= 0
            && (averagePerTransaction == null || averagePerTransaction.compareTo(BigDecimal.ZERO) >= 0)
            && !date.isAfter(LocalDate.now());
    }

    /**
     * Initializes computed fields based on the date
     */
    @Builder.Default
    private void initializeDayFields() {
        if (date != null) {
            this.dayOfWeek = date.getDayOfWeek();
            this.isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
            // Simplified business day logic - excludes weekends
            this.isBusinessDay = !Boolean.TRUE.equals(isWeekend);
        }
    }

    /**
     * Creates a sanitized copy suitable for external APIs
     * 
     * @return sanitized copy without internal metadata
     */
    public DailySpending sanitizedCopy() {
        return this.toBuilder()
            .metadata(null)
            .build();
    }

    /**
     * Natural ordering by date
     */
    @Override
    public int compareTo(DailySpending other) {
        if (other == null || other.date == null) {
            return this.date == null ? 0 : 1;
        }
        if (this.date == null) {
            return -1;
        }
        return this.date.compareTo(other.date);
    }

    /**
     * Spending metadata inner class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Additional metadata for spending analysis")
    public static class SpendingMetadata implements Serializable {
        
        @Serial
        private static final long serialVersionUID = 1L;

        @JsonProperty("weatherCondition")
        @Schema(description = "Weather condition for the day", example = "SUNNY")
        private String weatherCondition;

        @JsonProperty("specialEvents")
        @Schema(description = "Special events or holidays for the day")
        private String specialEvents;

        @JsonProperty("dataQuality")
        @Schema(description = "Data quality score", example = "0.95")
        private BigDecimal dataQuality;

        @JsonProperty("aggregationSource")
        @Schema(description = "Source of aggregation", example = "REAL_TIME")
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
        if (!(o instanceof DailySpending that)) return false;
        return Objects.equals(date, that.date);
    }

    /**
     * Custom hashCode method for better performance
     */
    @Override
    public int hashCode() {
        return Objects.hash(date);
    }
}