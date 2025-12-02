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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Hourly Spending Data Transfer Object
 * 
 * Represents hourly spending analytics for time-based pattern analysis.
 * This DTO follows Domain-Driven Design principles and includes comprehensive
 * validation, timezone support, and peak hour detection capabilities.
 * 
 * <p>Used for:
 * <ul>
 *   <li>Hourly spending pattern analysis</li>
 *   <li>Peak hour identification and analysis</li>
 *   <li>Time-of-day spending behavior insights</li>
 *   <li>Customer behavior optimization</li>
 *   <li>Fraud detection based on unusual timing</li>
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
@ToString(exclude = {"timezoneInfo"})
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(
    name = "HourlySpending",
    description = "Hourly spending analytics with timezone support and peak hour detection",
    example = """
        {
          "hour": 14,
          "amount": 245.80,
          "transactionCount": 12,
          "averagePerTransaction": 20.48,
          "isPeakHour": true,
          "timeSlot": "AFTERNOON",
          "timezone": "America/New_York",
          "date": "2024-01-15",
          "currency": "USD",
          "version": "1.0"
        }
        """
)
public class HourlySpending implements Serializable, Comparable<HourlySpending> {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Hour of the day (0-23 in 24-hour format)
     */
    @NotNull(message = "Hour cannot be null")
    @Min(value = 0, message = "Hour must be between 0 and 23")
    @Max(value = 23, message = "Hour must be between 0 and 23")
    @JsonProperty("hour")
    @JsonPropertyDescription("Hour of the day in 24-hour format (0-23)")
    @Schema(
        description = "Hour of the day in 24-hour format",
        example = "14",
        minimum = "0",
        maximum = "23",
        required = true
    )
    private Integer hour;

    /**
     * Total spending amount for this hour
     */
    @NotNull(message = "Amount cannot be null")
    @DecimalMin(value = "0.00", message = "Amount must be non-negative")
    @JsonProperty("amount")
    @JsonPropertyDescription("Total spending amount for this hour")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Total spending amount for this hour",
        example = "245.80",
        minimum = "0",
        required = true
    )
    private BigDecimal amount;

    /**
     * Number of transactions in this hour
     */
    @Min(value = 0, message = "Transaction count must be non-negative")
    @JsonProperty("transactionCount")
    @JsonPropertyDescription("Number of transactions in this hour")
    @Schema(
        description = "Number of transactions in this hour",
        example = "12",
        minimum = "0",
        required = true
    )
    private long transactionCount;

    /**
     * Average amount per transaction for this hour
     */
    @DecimalMin(value = "0.00", message = "Average per transaction must be non-negative")
    @JsonProperty("averagePerTransaction")
    @JsonPropertyDescription("Average amount per transaction for this hour")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Average amount per transaction for this hour",
        example = "20.48",
        minimum = "0"
    )
    private BigDecimal averagePerTransaction;

    /**
     * Whether this hour is identified as a peak spending hour
     */
    @JsonProperty("isPeakHour")
    @JsonPropertyDescription("Whether this hour is identified as a peak spending hour")
    @Schema(
        description = "Whether this hour is identified as a peak spending hour",
        example = "true"
    )
    private Boolean isPeakHour;

    /**
     * Time slot categorization for this hour
     */
    @JsonProperty("timeSlot")
    @JsonPropertyDescription("Time slot categorization (EARLY_MORNING, MORNING, AFTERNOON, EVENING, NIGHT)")
    @Schema(
        description = "Time slot categorization",
        example = "AFTERNOON",
        allowableValues = {"EARLY_MORNING", "MORNING", "AFTERNOON", "EVENING", "NIGHT"}
    )
    private TimeSlot timeSlot;

    /**
     * Timezone for this hourly data
     */
    @JsonProperty("timezone")
    @JsonPropertyDescription("Timezone identifier for this hourly data")
    @Schema(
        description = "Timezone identifier",
        example = "America/New_York"
    )
    private String timezone;

    /**
     * Date for this hourly spending data
     */
    @JsonProperty("date")
    @JsonPropertyDescription("Date for this hourly spending data")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @Schema(
        description = "Date for this hourly spending data",
        example = "2024-01-15",
        format = "date"
    )
    private LocalDate date;

    /**
     * Hour-over-hour percentage change
     */
    @JsonProperty("hourOverHourChange")
    @JsonPropertyDescription("Hour-over-hour percentage change from previous hour")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Hour-over-hour percentage change",
        example = "15.3"
    )
    private BigDecimal hourOverHourChange;

    /**
     * Day-of-week average for this hour
     */
    @DecimalMin(value = "0.00", message = "Day-of-week average must be non-negative")
    @JsonProperty("dayOfWeekAverage")
    @JsonPropertyDescription("Average spending for this hour across same day of week")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Average spending for this hour across same day of week",
        example = "220.45",
        minimum = "0"
    )
    private BigDecimal dayOfWeekAverage;

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
     * Timezone information for advanced time analysis
     */
    @JsonProperty("timezoneInfo")
    @JsonPropertyDescription("Additional timezone information")
    @Schema(description = "Additional timezone information for analysis")
    private TimezoneInfo timezoneInfo;

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
     * Determines if this is a peak hour based on threshold percentage
     * 
     * @param dailyAverage the daily average spending per hour
     * @param thresholdPercentage percentage above average to consider peak (e.g., 150 for 150%)
     * @return true if this hour exceeds the peak threshold
     */
    public boolean isPeakHour(BigDecimal dailyAverage, double thresholdPercentage) {
        if (amount == null || dailyAverage == null || dailyAverage.compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }
        
        BigDecimal threshold = dailyAverage.multiply(BigDecimal.valueOf(thresholdPercentage / 100.0));
        return amount.compareTo(threshold) > 0;
    }

    /**
     * Gets the time slot for this hour
     * 
     * @return time slot categorization
     */
    public TimeSlot getTimeSlot() {
        if (timeSlot != null) {
            return timeSlot;
        }
        return TimeSlot.fromHour(hour != null ? hour : 0);
    }

    /**
     * Converts this hour to a specific timezone
     * 
     * @param targetTimezone the target timezone
     * @return new HourlySpending object in the target timezone
     */
    public HourlySpending convertToTimezone(ZoneId targetTimezone) {
        if (hour == null || date == null || timezone == null) {
            return this;
        }
        
        ZoneId sourceTimezone = ZoneId.of(timezone);
        LocalDateTime localDateTime = LocalDateTime.of(date, LocalTime.of(hour, 0));
        ZonedDateTime sourceTime = localDateTime.atZone(sourceTimezone);
        ZonedDateTime targetTime = sourceTime.withZoneSameInstant(targetTimezone);
        
        return this.toBuilder()
            .hour(targetTime.getHour())
            .date(targetTime.toLocalDate())
            .timezone(targetTimezone.getId())
            .build();
    }

    /**
     * Checks if this hour represents unusual spending patterns
     * 
     * @param hourlyAverage average spending for this hour across historical data
     * @param thresholdMultiplier multiplier for anomaly detection (e.g., 2.0 for 200%)
     * @return true if spending is anomalous
     */
    public boolean isAnomalousSpending(BigDecimal hourlyAverage, double thresholdMultiplier) {
        if (amount == null || hourlyAverage == null || hourlyAverage.compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }
        
        BigDecimal threshold = hourlyAverage.multiply(BigDecimal.valueOf(thresholdMultiplier));
        return amount.compareTo(threshold) > 0;
    }

    /**
     * Gets business hour classification
     * 
     * @return true if this is a typical business hour (9 AM - 5 PM)
     */
    public boolean isBusinessHour() {
        return hour != null && hour >= 9 && hour <= 17;
    }

    /**
     * Validates the business rules for this hourly spending data
     * 
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return hour != null && hour >= 0 && hour <= 23
            && amount != null && amount.compareTo(BigDecimal.ZERO) >= 0
            && (averagePerTransaction == null || averagePerTransaction.compareTo(BigDecimal.ZERO) >= 0);
    }

    /**
     * Creates a sanitized copy suitable for external APIs
     * 
     * @return sanitized copy without internal timezone info
     */
    public HourlySpending sanitizedCopy() {
        return this.toBuilder()
            .timezoneInfo(null)
            .build();
    }

    /**
     * Natural ordering by hour
     */
    @Override
    public int compareTo(HourlySpending other) {
        if (other == null || other.hour == null) {
            return this.hour == null ? 0 : 1;
        }
        if (this.hour == null) {
            return -1;
        }
        
        // First compare by date if available
        if (this.date != null && other.date != null) {
            int dateComparison = this.date.compareTo(other.date);
            if (dateComparison != 0) {
                return dateComparison;
            }
        }
        
        // Then compare by hour
        return this.hour.compareTo(other.hour);
    }

    /**
     * Time slot enumeration
     */
    public enum TimeSlot {
        EARLY_MORNING(0, 5),   // 00:00 - 05:59
        MORNING(6, 11),        // 06:00 - 11:59
        AFTERNOON(12, 17),     // 12:00 - 17:59
        EVENING(18, 21),       // 18:00 - 21:59
        NIGHT(22, 23);         // 22:00 - 23:59

        private final int startHour;
        private final int endHour;

        TimeSlot(int startHour, int endHour) {
            this.startHour = startHour;
            this.endHour = endHour;
        }

        public static TimeSlot fromHour(int hour) {
            for (TimeSlot slot : values()) {
                if (hour >= slot.startHour && hour <= slot.endHour) {
                    return slot;
                }
            }
            return NIGHT; // Default fallback
        }

        public int getStartHour() { return startHour; }
        public int getEndHour() { return endHour; }
    }

    /**
     * Timezone information inner class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Additional timezone information for analysis")
    public static class TimezoneInfo implements Serializable {
        
        @Serial
        private static final long serialVersionUID = 1L;

        @JsonProperty("utcOffset")
        @Schema(description = "UTC offset in hours", example = "-5")
        private Integer utcOffset;

        @JsonProperty("isDaylightSaving")
        @Schema(description = "Whether daylight saving time is active", example = "false")
        private Boolean isDaylightSaving;

        @JsonProperty("localBusinessHours")
        @Schema(description = "Local business hours consideration", example = "WITHIN_BUSINESS_HOURS")
        private String localBusinessHours;

        @JsonProperty("marketSession")
        @Schema(description = "Market session classification", example = "REGULAR_HOURS")
        private String marketSession;
    }

    /**
     * Custom equals method for better performance
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HourlySpending that)) return false;
        return Objects.equals(hour, that.hour) && Objects.equals(date, that.date);
    }

    /**
     * Custom hashCode method for better performance
     */
    @Override
    public int hashCode() {
        return Objects.hash(hour, date);
    }
}