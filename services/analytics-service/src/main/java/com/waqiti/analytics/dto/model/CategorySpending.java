package com.waqiti.analytics.dto.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
import java.time.Instant;
import java.util.Objects;

/**
 * Category Spending Data Transfer Object
 * 
 * Represents spending analytics aggregated by category for financial analysis.
 * This DTO follows Domain-Driven Design principles and includes comprehensive
 * validation, audit fields, and defensive programming practices.
 * 
 * <p>Used for:
 * <ul>
 *   <li>Category-based spending analysis and reporting</li>
 *   <li>Budget tracking and variance analysis</li>
 *   <li>Financial behavior pattern identification</li>
 *   <li>Merchant and transaction categorization insights</li>
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
@ToString(exclude = {"auditMetadata"})
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(
    name = "CategorySpending",
    description = "Category-based spending analytics with comprehensive metrics and audit information",
    example = """
        {
          "categoryName": "Groceries",
          "amount": 1250.75,
          "transactionCount": 45,
          "percentage": 15.2,
          "averagePerTransaction": 27.79,
          "monthOverMonthChange": 5.3,
          "categoryCode": "GRC",
          "periodStart": "2024-01-01T00:00:00Z",
          "periodEnd": "2024-01-31T23:59:59Z",
          "currency": "USD",
          "version": "1.0"
        }
        """
)
public class CategorySpending implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The name of the spending category
     */
    @NotBlank(message = "Category name cannot be blank")
    @Size(min = 1, max = 100, message = "Category name must be between 1 and 100 characters")
    @JsonProperty("categoryName")
    @JsonPropertyDescription("The human-readable name of the spending category")
    @Schema(
        description = "The name of the spending category",
        example = "Groceries",
        minLength = 1,
        maxLength = 100,
        required = true
    )
    private String categoryName;

    /**
     * Total spending amount for this category
     */
    @NotNull(message = "Amount cannot be null")
    @DecimalMin(value = "0.00", message = "Amount must be non-negative")
    @JsonProperty("amount")
    @JsonPropertyDescription("Total spending amount for this category")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Total spending amount for this category",
        example = "1250.75",
        minimum = "0",
        required = true
    )
    private BigDecimal amount;

    /**
     * Number of transactions in this category
     */
    @Min(value = 0, message = "Transaction count must be non-negative")
    @JsonProperty("transactionCount")
    @JsonPropertyDescription("Number of transactions in this category")
    @Schema(
        description = "Number of transactions in this category",
        example = "45",
        minimum = "0",
        required = true
    )
    private long transactionCount;

    /**
     * Percentage of total spending this category represents
     */
    @DecimalMin(value = "0.00", message = "Percentage must be non-negative")
    @JsonProperty("percentage")
    @JsonPropertyDescription("Percentage of total spending this category represents")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Percentage of total spending this category represents",
        example = "15.2",
        minimum = "0",
        maximum = "100"
    )
    private BigDecimal percentage;

    /**
     * Average amount per transaction in this category
     */
    @DecimalMin(value = "0.00", message = "Average per transaction must be non-negative")
    @JsonProperty("averagePerTransaction")
    @JsonPropertyDescription("Average amount per transaction in this category")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Average amount per transaction in this category",
        example = "27.79",
        minimum = "0"
    )
    private BigDecimal averagePerTransaction;

    /**
     * Month-over-month percentage change
     */
    @JsonProperty("monthOverMonthChange")
    @JsonPropertyDescription("Month-over-month percentage change (positive for increase, negative for decrease)")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Month-over-month percentage change",
        example = "5.3"
    )
    private BigDecimal monthOverMonthChange;

    /**
     * Category code for system identification
     */
    @Size(max = 10, message = "Category code must not exceed 10 characters")
    @JsonProperty("categoryCode")
    @JsonPropertyDescription("System-generated category code for identification")
    @Schema(
        description = "System-generated category code",
        example = "GRC",
        maxLength = 10
    )
    private String categoryCode;

    /**
     * Start of the analysis period
     */
    @JsonProperty("periodStart")
    @JsonPropertyDescription("Start timestamp of the analysis period")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    @Schema(
        description = "Start of the analysis period",
        example = "2024-01-01T00:00:00Z",
        format = "date-time"
    )
    private Instant periodStart;

    /**
     * End of the analysis period
     */
    @JsonProperty("periodEnd")
    @JsonPropertyDescription("End timestamp of the analysis period")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    @Schema(
        description = "End of the analysis period",
        example = "2024-01-31T23:59:59Z",
        format = "date-time"
    )
    private Instant periodEnd;

    /**
     * Currency code for the amounts
     */
    @Size(min = 3, max = 3, message = "Currency must be a 3-character ISO code")
    @JsonProperty("currency")
    @JsonPropertyDescription("ISO 4217 currency code")
    @Schema(
        description = "ISO 4217 currency code",
        example = "USD",
        pattern = "^[A-Z]{3}$",
        minLength = 3,
        maxLength = 3
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
     * Audit metadata for tracking
     */
    @JsonProperty("auditMetadata")
    @JsonPropertyDescription("Audit information for tracking purposes")
    @Schema(description = "Audit metadata for tracking")
    private AuditMetadata auditMetadata;

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
     * Validates the business rules for this spending data
     * 
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return categoryName != null && !categoryName.trim().isEmpty()
            && amount != null && amount.compareTo(BigDecimal.ZERO) >= 0
            && (percentage == null || percentage.compareTo(BigDecimal.ZERO) >= 0)
            && (averagePerTransaction == null || averagePerTransaction.compareTo(BigDecimal.ZERO) >= 0);
    }

    /**
     * Creates a sanitized copy suitable for external APIs
     * 
     * @return sanitized copy without internal audit metadata
     */
    public CategorySpending sanitizedCopy() {
        return this.toBuilder()
            .auditMetadata(null)
            .build();
    }

    /**
     * Compares this category spending with another for trend analysis
     * 
     * @param other the other category spending to compare with
     * @return comparison result indicating trend direction
     */
    public TrendDirection compareTrend(CategorySpending other) {
        if (other == null || other.amount == null || this.amount == null) {
            return TrendDirection.UNKNOWN;
        }
        
        int comparison = this.amount.compareTo(other.amount);
        if (comparison > 0) {
            return TrendDirection.INCREASING;
        } else if (comparison < 0) {
            return TrendDirection.DECREASING;
        } else {
            return TrendDirection.STABLE;
        }
    }

    /**
     * Audit metadata inner class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Audit metadata for tracking and compliance")
    public static class AuditMetadata implements Serializable {
        
        @Serial
        private static final long serialVersionUID = 1L;

        @JsonProperty("createdAt")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        @Schema(description = "Creation timestamp", format = "date-time")
        private Instant createdAt;

        @JsonProperty("lastModifiedAt")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        @Schema(description = "Last modification timestamp", format = "date-time")
        private Instant lastModifiedAt;

        @JsonProperty("dataSource")
        @Schema(description = "Source system that generated this data", example = "transaction-service")
        private String dataSource;

        @JsonProperty("calculationMethod")
        @Schema(description = "Method used for calculation", example = "REAL_TIME_AGGREGATION")
        private String calculationMethod;
    }

    /**
     * Trend direction enumeration
     */
    public enum TrendDirection {
        INCREASING, DECREASING, STABLE, UNKNOWN
    }

    /**
     * Custom equals method for better performance
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CategorySpending that)) return false;
        return Objects.equals(categoryName, that.categoryName) && 
               Objects.equals(categoryCode, that.categoryCode);
    }

    /**
     * Custom hashCode method for better performance
     */
    @Override
    public int hashCode() {
        return Objects.hash(categoryName, categoryCode);
    }
}