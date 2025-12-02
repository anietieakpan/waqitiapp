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
import java.time.LocalDate;
import java.util.Objects;

/**
 * Spending Comparison Data Transfer Object
 * 
 * Represents peer comparison and benchmarking analysis with privacy-preserving aggregations.
 * This DTO follows Domain-Driven Design principles and includes comprehensive
 * validation, demographic comparisons, and anonymized benchmarking capabilities.
 * 
 * <p>Used for:
 * <ul>
 *   <li>Peer spending comparison and benchmarking</li>
 *   <li>Demographic group analysis</li>
 *   <li>Market segment positioning</li>
 *   <li>Privacy-preserving competitive insights</li>
 *   <li>Financial behavior normalization</li>
 *   <li>Goal setting and recommendation systems</li>
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
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"comparisonMetadata"})
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(
    name = "SpendingComparison",
    description = "Peer comparison and benchmarking analysis with privacy-preserving aggregations",
    example = """
        {
          "comparisonId": "comparison_12345",
          "userId": "user_67890",
          "userAmount": 2850.75,
          "peerGroupAverage": 3125.50,
          "peerGroupMedian": 2950.00,
          "percentileRank": 42,
          "comparisonPeriod": "MONTHLY",
          "demographicGroup": "Age_25_34_Income_50K_75K",
          "sampleSize": 1250,
          "variance": -8.8,
          "performanceRating": "BELOW_AVERAGE",
          "currency": "USD",
          "version": "1.0"
        }
        """
)
public class SpendingComparison implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier for this comparison analysis
     */
    @EqualsAndHashCode.Include
    @JsonProperty("comparisonId")
    @JsonPropertyDescription("Unique identifier for this comparison analysis")
    @Schema(
        description = "Unique identifier for this comparison analysis",
        example = "comparison_12345"
    )
    private String comparisonId;

    /**
     * User ID for this comparison
     */
    @NotNull(message = "User ID cannot be null")
    @EqualsAndHashCode.Include
    @JsonProperty("userId")
    @JsonPropertyDescription("User ID for this comparison")
    @Schema(
        description = "User ID for this comparison",
        example = "user_67890",
        required = true
    )
    private String userId;

    /**
     * User's spending amount for the comparison period
     */
    @NotNull(message = "User amount cannot be null")
    @DecimalMin(value = "0.00", message = "User amount must be non-negative")
    @JsonProperty("userAmount")
    @JsonPropertyDescription("User's spending amount for the comparison period")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "User's spending amount for the comparison period",
        example = "2850.75",
        minimum = "0",
        required = true
    )
    private BigDecimal userAmount;

    /**
     * Peer group average spending amount
     */
    @DecimalMin(value = "0.00", message = "Peer group average must be non-negative")
    @JsonProperty("peerGroupAverage")
    @JsonPropertyDescription("Peer group average spending amount")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Peer group average spending amount",
        example = "3125.50",
        minimum = "0"
    )
    private BigDecimal peerGroupAverage;

    /**
     * Peer group median spending amount
     */
    @DecimalMin(value = "0.00", message = "Peer group median must be non-negative")
    @JsonProperty("peerGroupMedian")
    @JsonPropertyDescription("Peer group median spending amount")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Peer group median spending amount",
        example = "2950.00",
        minimum = "0"
    )
    private BigDecimal peerGroupMedian;

    /**
     * 25th percentile of peer group
     */
    @DecimalMin(value = "0.00", message = "25th percentile must be non-negative")
    @JsonProperty("peerGroup25thPercentile")
    @JsonPropertyDescription("25th percentile of peer group spending")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "25th percentile of peer group spending",
        example = "2200.00",
        minimum = "0"
    )
    private BigDecimal peerGroup25thPercentile;

    /**
     * 75th percentile of peer group
     */
    @DecimalMin(value = "0.00", message = "75th percentile must be non-negative")
    @JsonProperty("peerGroup75thPercentile")
    @JsonPropertyDescription("75th percentile of peer group spending")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "75th percentile of peer group spending",
        example = "3800.00",
        minimum = "0"
    )
    private BigDecimal peerGroup75thPercentile;

    /**
     * User's percentile rank within the peer group
     */
    @Min(value = 0, message = "Percentile rank must be between 0 and 100")
    @JsonProperty("percentileRank")
    @JsonPropertyDescription("User's percentile rank within the peer group (0-100)")
    @Schema(
        description = "User's percentile rank within the peer group",
        example = "42",
        minimum = "0",
        maximum = "100"
    )
    private Integer percentileRank;

    /**
     * Time period for the comparison
     */
    @JsonProperty("comparisonPeriod")
    @JsonPropertyDescription("Time period for the comparison analysis")
    @Schema(
        description = "Time period for the comparison",
        example = "MONTHLY",
        allowableValues = {"WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY"}
    )
    private ComparisonPeriod comparisonPeriod;

    /**
     * Demographic group identifier (anonymized)
     */
    @Size(max = 100, message = "Demographic group must not exceed 100 characters")
    @JsonProperty("demographicGroup")
    @JsonPropertyDescription("Anonymized demographic group identifier")
    @Schema(
        description = "Anonymized demographic group identifier",
        example = "Age_25_34_Income_50K_75K",
        maxLength = 100
    )
    private String demographicGroup;

    /**
     * Geographic region for comparison
     */
    @Size(max = 50, message = "Geographic region must not exceed 50 characters")
    @JsonProperty("geographicRegion")
    @JsonPropertyDescription("Geographic region for comparison (anonymized)")
    @Schema(
        description = "Geographic region for comparison",
        example = "Northeast_Urban",
        maxLength = 50
    )
    private String geographicRegion;

    /**
     * Sample size of the peer group
     */
    @Min(value = 10, message = "Sample size must be at least 10 for statistical validity")
    @JsonProperty("sampleSize")
    @JsonPropertyDescription("Sample size of the peer group used for comparison")
    @Schema(
        description = "Sample size of the peer group",
        example = "1250",
        minimum = "10"
    )
    private Integer sampleSize;

    /**
     * Variance from peer group average as percentage
     */
    @JsonProperty("variance")
    @JsonPropertyDescription("Variance from peer group average as percentage")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Variance from peer group average as percentage",
        example = "-8.8"
    )
    private BigDecimal variance;

    /**
     * Variance from peer group median as percentage
     */
    @JsonProperty("medianVariance")
    @JsonPropertyDescription("Variance from peer group median as percentage")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Variance from peer group median as percentage",
        example = "-3.4"
    )
    private BigDecimal medianVariance;

    /**
     * Performance rating compared to peers
     */
    @JsonProperty("performanceRating")
    @JsonPropertyDescription("Performance rating compared to peers")
    @Schema(
        description = "Performance rating compared to peers",
        example = "BELOW_AVERAGE",
        allowableValues = {"WELL_BELOW_AVERAGE", "BELOW_AVERAGE", "AVERAGE", "ABOVE_AVERAGE", "WELL_ABOVE_AVERAGE"}
    )
    private PerformanceRating performanceRating;

    /**
     * Spending category for this comparison
     */
    @Size(max = 50, message = "Category must not exceed 50 characters")
    @JsonProperty("category")
    @JsonPropertyDescription("Spending category for this comparison (null for total spending)")
    @Schema(
        description = "Spending category for this comparison",
        example = "Groceries",
        maxLength = 50
    )
    private String category;

    /**
     * Analysis start date
     */
    @JsonProperty("analysisStartDate")
    @JsonPropertyDescription("Start date of the analysis period")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @Schema(
        description = "Start date of the analysis period",
        example = "2024-01-01",
        format = "date"
    )
    private LocalDate analysisStartDate;

    /**
     * Analysis end date
     */
    @JsonProperty("analysisEndDate")
    @JsonPropertyDescription("End date of the analysis period")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @Schema(
        description = "End date of the analysis period",
        example = "2024-01-31",
        format = "date"
    )
    private LocalDate analysisEndDate;

    /**
     * Confidence interval for the comparison
     */
    @DecimalMin(value = "0.0", message = "Confidence interval must be non-negative")
    @JsonProperty("confidenceInterval")
    @JsonPropertyDescription("Confidence interval for the comparison (95% default)")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Confidence interval for the comparison",
        example = "95.0",
        minimum = "0",
        maximum = "100"
    )
    private BigDecimal confidenceInterval;

    /**
     * Statistical significance indicator
     */
    @JsonProperty("isStatisticallySignificant")
    @JsonPropertyDescription("Whether the difference is statistically significant")
    @Schema(
        description = "Whether the difference is statistically significant",
        example = "true"
    )
    private Boolean isStatisticallySignificant;

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
     * When this comparison was generated
     */
    @JsonProperty("generatedAt")
    @JsonPropertyDescription("When this comparison was generated")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    @Schema(
        description = "When this comparison was generated",
        format = "date-time"
    )
    private Instant generatedAt;

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
     * Comparison metadata for additional information
     */
    @JsonProperty("comparisonMetadata")
    @JsonPropertyDescription("Additional metadata for comparison analysis")
    @Schema(description = "Additional metadata for comparison analysis")
    private ComparisonMetadata comparisonMetadata;

    /**
     * Calculates the variance from peer group average
     * 
     * @return variance percentage (positive for above average, negative for below)
     */
    public BigDecimal calculateVarianceFromAverage() {
        if (peerGroupAverage == null || userAmount == null || peerGroupAverage.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal variance = userAmount.subtract(peerGroupAverage);
        return variance.divide(peerGroupAverage, 4, RoundingMode.HALF_UP)
                      .multiply(BigDecimal.valueOf(100))
                      .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates the variance from peer group median
     * 
     * @return variance percentage from median
     */
    public BigDecimal calculateVarianceFromMedian() {
        if (peerGroupMedian == null || userAmount == null || peerGroupMedian.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal variance = userAmount.subtract(peerGroupMedian);
        return variance.divide(peerGroupMedian, 4, RoundingMode.HALF_UP)
                      .multiply(BigDecimal.valueOf(100))
                      .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Determines the performance rating based on percentile rank
     * 
     * @return calculated performance rating
     */
    public PerformanceRating calculatePerformanceRating() {
        if (percentileRank == null) {
            return PerformanceRating.AVERAGE;
        }
        
        int rank = percentileRank;
        if (rank >= 90) {
            return PerformanceRating.WELL_ABOVE_AVERAGE;
        } else if (rank >= 70) {
            return PerformanceRating.ABOVE_AVERAGE;
        } else if (rank >= 30) {
            return PerformanceRating.AVERAGE;
        } else if (rank >= 10) {
            return PerformanceRating.BELOW_AVERAGE;
        } else {
            return PerformanceRating.WELL_BELOW_AVERAGE;
        }
    }

    /**
     * Checks if the user is in the top quartile
     * 
     * @return true if user is in top 25%
     */
    public boolean isTopQuartile() {
        return percentileRank != null && percentileRank >= 75;
    }

    /**
     * Checks if the user is in the bottom quartile
     * 
     * @return true if user is in bottom 25%
     */
    public boolean isBottomQuartile() {
        return percentileRank != null && percentileRank <= 25;
    }

    /**
     * Gets the savings opportunity compared to average
     * 
     * @return potential savings if user spent at peer average
     */
    public BigDecimal getSavingsOpportunity() {
        if (userAmount == null || peerGroupAverage == null || userAmount.compareTo(peerGroupAverage) <= 0) {
            return BigDecimal.ZERO;
        }
        return userAmount.subtract(peerGroupAverage);
    }

    /**
     * Gets the spending gap to reach average
     * 
     * @return amount needed to reach peer average (negative if already above)
     */
    public BigDecimal getSpendingGapToAverage() {
        if (userAmount == null || peerGroupAverage == null) {
            return BigDecimal.ZERO;
        }
        return peerGroupAverage.subtract(userAmount);
    }

    /**
     * Validates the business rules for this comparison data
     * 
     * @return true if valid, false otherwise
     */
    public boolean isValid() {\n        return userId != null\n            && userAmount != null && userAmount.compareTo(BigDecimal.ZERO) >= 0\n            && (peerGroupAverage == null || peerGroupAverage.compareTo(BigDecimal.ZERO) >= 0)\n            && (peerGroupMedian == null || peerGroupMedian.compareTo(BigDecimal.ZERO) >= 0)\n            && (percentileRank == null || (percentileRank >= 0 && percentileRank <= 100))\n            && (sampleSize == null || sampleSize >= 10);\n    }\n\n    /**\n     * Creates a sanitized copy suitable for external APIs\n     * \n     * @return sanitized copy without internal metadata\n     */\n    public SpendingComparison sanitizedCopy() {\n        return this.toBuilder()\n            .comparisonMetadata(null)\n            .build();\n    }\n\n    /**\n     * Comparison period enumeration\n     */\n    public enum ComparisonPeriod {\n        WEEKLY, MONTHLY, QUARTERLY, YEARLY\n    }\n\n    /**\n     * Performance rating enumeration\n     */\n    public enum PerformanceRating {\n        WELL_BELOW_AVERAGE,\n        BELOW_AVERAGE,\n        AVERAGE,\n        ABOVE_AVERAGE,\n        WELL_ABOVE_AVERAGE\n    }\n\n    /**\n     * Comparison metadata inner class\n     */\n    @Data\n    @Builder\n    @NoArgsConstructor\n    @AllArgsConstructor\n    @JsonInclude(JsonInclude.Include.NON_NULL)\n    @Schema(description = \"Additional metadata for comparison analysis\")\n    public static class ComparisonMetadata implements Serializable {\n        \n        @Serial\n        private static final long serialVersionUID = 1L;\n\n        @JsonProperty(\"privacyLevel\")\n        @Schema(description = \"Level of privacy protection applied\", example = \"HIGH\")\n        private String privacyLevel;\n\n        @JsonProperty(\"aggregationMethod\")\n        @Schema(description = \"Method used for peer group aggregation\", example = \"DIFFERENTIAL_PRIVACY\")\n        private String aggregationMethod;\n\n        @JsonProperty(\"dataFreshness\")\n        @Schema(description = \"How recent the peer group data is in days\", example = \"7\")\n        private Integer dataFreshness;\n\n        @JsonProperty(\"excludedOutliers\")\n        @Schema(description = \"Number of outliers excluded from peer group\", example = \"15\")\n        private Integer excludedOutliers;\n\n        @JsonProperty(\"demographicFilters\")\n        @Schema(description = \"Filters applied for demographic matching\")\n        private String demographicFilters;\n\n        @JsonProperty(\"confidenceScore\")\n        @Schema(description = \"Confidence score for the comparison\", example = \"0.92\")\n        private BigDecimal confidenceScore;\n\n        @JsonProperty(\"standardError\")\n        @Schema(description = \"Standard error of the comparison\", example = \"45.30\")\n        private BigDecimal standardError;\n\n        @JsonProperty(\"lastUpdated\")\n        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = \"yyyy-MM-dd'T'HH:mm:ss'Z'\", timezone = \"UTC\")\n        @Schema(description = \"Last update timestamp\", format = \"date-time\")\n        private Instant lastUpdated;\n    }\n\n    /**\n     * Custom equals method for better performance\n     */\n    @Override\n    public boolean equals(Object o) {\n        if (this == o) return true;\n        if (!(o instanceof SpendingComparison that)) return false;\n        return Objects.equals(comparisonId, that.comparisonId) && \n               Objects.equals(userId, that.userId);\n    }\n\n    /**\n     * Custom hashCode method for better performance\n     */\n    @Override\n    public int hashCode() {\n        return Objects.hash(comparisonId, userId);\n    }\n}