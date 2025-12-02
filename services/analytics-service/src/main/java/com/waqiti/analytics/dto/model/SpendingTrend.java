package com.waqiti.analytics.dto.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

/**
 * Spending Trend Data Transfer Object
 * 
 * Represents comprehensive trend analysis with statistical metrics for spending behavior.
 * This DTO follows Domain-Driven Design principles and includes comprehensive
 * validation, statistical calculations, and predictive analytics support.
 * 
 * <p>Used for:
 * <ul>
 *   <li>Trend analysis and pattern recognition</li>
 *   <li>Statistical modeling and forecasting</li>
 *   <li>Behavioral pattern identification</li>
 *   <li>Predictive analytics and recommendations</li>
 *   <li>Anomaly detection and alerts</li>
 *   <li>Long-term financial planning</li>
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
@ToString(exclude = {"trendMetadata"})
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(
    name = "SpendingTrend",
    description = "Comprehensive spending trend analysis with statistical metrics and predictions",
    example = """
        {
          "trendId": "trend_12345",
          "analysisStartDate": "2024-01-01",
          "analysisEndDate": "2024-01-31",
          "trendDirection": "INCREASING",
          "trendStrength": "MODERATE",
          "changeRate": 15.7,
          "slope": 2.35,
          "correlation": 0.82,
          "seasonality": "HIGH",
          "volatility": "LOW",
          "confidence": 0.89,
          "predictedNextPeriod": 4125.50,
          "currency": "USD",
          "version": "1.0"
        }
        """
)
public class SpendingTrend implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier for this trend analysis
     */
    @JsonProperty("trendId")
    @JsonPropertyDescription("Unique identifier for this trend analysis")
    @Schema(
        description = "Unique identifier for this trend analysis",
        example = "trend_12345"
    )
    private String trendId;

    /**
     * Start date of the trend analysis period
     */
    @NotNull(message = "Analysis start date cannot be null")
    @JsonProperty("analysisStartDate")
    @JsonPropertyDescription("Start date of the trend analysis period")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @Schema(
        description = "Start date of the trend analysis period",
        example = "2024-01-01",
        format = "date",
        required = true
    )
    private LocalDate analysisStartDate;

    /**
     * End date of the trend analysis period
     */
    @NotNull(message = "Analysis end date cannot be null")
    @JsonProperty("analysisEndDate")
    @JsonPropertyDescription("End date of the trend analysis period")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @Schema(
        description = "End date of the trend analysis period",
        example = "2024-01-31",
        format = "date",
        required = true
    )
    private LocalDate analysisEndDate;

    /**
     * Overall trend direction
     */
    @JsonProperty("trendDirection")
    @JsonPropertyDescription("Overall trend direction (INCREASING, DECREASING, STABLE, VOLATILE)")
    @Schema(
        description = "Overall trend direction",
        example = "INCREASING",
        allowableValues = {"INCREASING", "DECREASING", "STABLE", "VOLATILE", "UNKNOWN"}
    )
    private TrendDirection trendDirection;

    /**
     * Strength of the trend
     */
    @JsonProperty("trendStrength")
    @JsonPropertyDescription("Strength of the trend (WEAK, MODERATE, STRONG)")
    @Schema(
        description = "Strength of the trend",
        example = "MODERATE",
        allowableValues = {"WEAK", "MODERATE", "STRONG", "UNKNOWN"}
    )
    private TrendStrength trendStrength;

    /**
     * Rate of change percentage
     */
    @JsonProperty("changeRate")
    @JsonPropertyDescription("Rate of change percentage over the analysis period")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Rate of change percentage",
        example = "15.7"
    )
    private BigDecimal changeRate;

    /**
     * Linear regression slope
     */
    @JsonProperty("slope")
    @JsonPropertyDescription("Linear regression slope indicating trend steepness")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.####")
    @Schema(
        description = "Linear regression slope",
        example = "2.35"
    )
    private BigDecimal slope;

    /**
     * Correlation coefficient
     */
    @DecimalMin(value = "-1.0", message = "Correlation must be between -1 and 1")
    @DecimalMax(value = "1.0", message = "Correlation must be between -1 and 1")
    @JsonProperty("correlation")
    @JsonPropertyDescription("Correlation coefficient (-1 to 1)")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.####")
    @Schema(
        description = "Correlation coefficient indicating trend consistency",
        example = "0.82",
        minimum = "-1",
        maximum = "1"
    )
    private BigDecimal correlation;

    /**
     * R-squared value for trend fit
     */
    @DecimalMin(value = "0.0", message = "R-squared must be between 0 and 1")
    @DecimalMax(value = "1.0", message = "R-squared must be between 0 and 1")
    @JsonProperty("rSquared")
    @JsonPropertyDescription("R-squared value indicating goodness of fit")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.####")
    @Schema(
        description = "R-squared value for trend fit quality",
        example = "0.67",
        minimum = "0",
        maximum = "1"
    )
    private BigDecimal rSquared;

    /**
     * Seasonality indicator
     */
    @JsonProperty("seasonality")
    @JsonPropertyDescription("Seasonality level (NONE, LOW, MODERATE, HIGH)")
    @Schema(
        description = "Seasonality level",
        example = "HIGH",
        allowableValues = {"NONE", "LOW", "MODERATE", "HIGH", "UNKNOWN"}
    )
    private SeasonalityLevel seasonality;

    /**
     * Volatility level
     */
    @JsonProperty("volatility")
    @JsonPropertyDescription("Volatility level (LOW, MODERATE, HIGH)")
    @Schema(
        description = "Volatility level",
        example = "LOW",
        allowableValues = {"LOW", "MODERATE", "HIGH", "UNKNOWN"}
    )
    private VolatilityLevel volatility;

    /**
     * Standard deviation of the data
     */
    @DecimalMin(value = "0.0", message = "Standard deviation must be non-negative")
    @JsonProperty("standardDeviation")
    @JsonPropertyDescription("Standard deviation of spending amounts")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Standard deviation of spending amounts",
        example = "125.45",
        minimum = "0"
    )
    private BigDecimal standardDeviation;

    /**
     * Coefficient of variation
     */
    @DecimalMin(value = "0.0", message = "Coefficient of variation must be non-negative")
    @JsonProperty("coefficientOfVariation")
    @JsonPropertyDescription("Coefficient of variation (CV) as percentage")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Coefficient of variation as percentage",
        example = "12.8",
        minimum = "0"
    )
    private BigDecimal coefficientOfVariation;

    /**
     * Confidence level for the trend analysis
     */
    @DecimalMin(value = "0.0", message = "Confidence must be between 0 and 1")
    @DecimalMax(value = "1.0", message = "Confidence must be between 0 and 1")
    @JsonProperty("confidence")
    @JsonPropertyDescription("Confidence level for the trend analysis (0-1)")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.####")
    @Schema(
        description = "Confidence level for the trend analysis",
        example = "0.89",
        minimum = "0",
        maximum = "1"
    )
    private BigDecimal confidence;

    /**
     * Predicted value for the next period
     */
    @DecimalMin(value = "0.0", message = "Predicted value must be non-negative")
    @JsonProperty("predictedNextPeriod")
    @JsonPropertyDescription("Predicted spending amount for the next period")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Predicted spending amount for the next period",
        example = "4125.50",
        minimum = "0"
    )
    private BigDecimal predictedNextPeriod;

    /**
     * Prediction confidence interval lower bound
     */
    @DecimalMin(value = "0.0", message = "Lower bound must be non-negative")
    @JsonProperty("predictionLowerBound")
    @JsonPropertyDescription("Lower bound of prediction confidence interval")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Lower bound of prediction confidence interval",
        example = "3850.25",
        minimum = "0"
    )
    private BigDecimal predictionLowerBound;

    /**
     * Prediction confidence interval upper bound
     */
    @DecimalMin(value = "0.0", message = "Upper bound must be non-negative")
    @JsonProperty("predictionUpperBound")
    @JsonPropertyDescription("Upper bound of prediction confidence interval")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Upper bound of prediction confidence interval",
        example = "4400.75",
        minimum = "0"
    )
    private BigDecimal predictionUpperBound;

    /**
     * Number of data points used in analysis
     */
    @JsonProperty("dataPointsCount")
    @JsonPropertyDescription("Number of data points used in the trend analysis")
    @Schema(
        description = "Number of data points used in analysis",
        example = "31"
    )
    private Integer dataPointsCount;

    /**
     * Analysis method used
     */
    @JsonProperty("analysisMethod")
    @JsonPropertyDescription("Statistical method used for trend analysis")
    @Schema(
        description = "Statistical method used for trend analysis",
        example = "LINEAR_REGRESSION",
        allowableValues = {"LINEAR_REGRESSION", "POLYNOMIAL_REGRESSION", "EXPONENTIAL_SMOOTHING", "ARIMA", "SEASONAL_DECOMPOSITION"}
    )
    private String analysisMethod;

    /**
     * Currency code for monetary amounts
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
     * Trend metadata for additional information
     */
    @JsonProperty("trendMetadata")
    @JsonPropertyDescription("Additional metadata for trend analysis")
    @Schema(description = "Additional metadata for trend analysis")
    private TrendMetadata trendMetadata;

    /**
     * Calculates the analysis period length in days
     * 
     * @return number of days in the analysis period
     */
    public long getAnalysisPeriodDays() {
        if (analysisStartDate == null || analysisEndDate == null) {
            return 0;
        }
        return Period.between(analysisStartDate, analysisEndDate).getDays() + 1;
    }

    /**
     * Determines if the trend is statistically significant
     * 
     * @param significanceLevel the significance level (e.g., 0.05 for 95% confidence)
     * @return true if trend is statistically significant
     */
    public boolean isStatisticallySignificant(double significanceLevel) {
        return confidence != null && confidence.compareTo(BigDecimal.valueOf(1.0 - significanceLevel)) >= 0;
    }

    /**
     * Calculates the trend velocity (change per unit time)
     * 
     * @return trend velocity
     */
    public BigDecimal calculateTrendVelocity() {
        if (changeRate == null || getAnalysisPeriodDays() == 0) {
            return BigDecimal.ZERO;
        }
        return changeRate.divide(BigDecimal.valueOf(getAnalysisPeriodDays()), 4, RoundingMode.HALF_UP);
    }

    /**
     * Determines the trend quality based on multiple factors
     * 
     * @return trend quality assessment
     */
    public TrendQuality assessTrendQuality() {
        if (confidence == null || rSquared == null || dataPointsCount == null) {
            return TrendQuality.UNKNOWN;
        }
        
        double confidenceValue = confidence.doubleValue();
        double rSquaredValue = rSquared.doubleValue();
        int dataPoints = dataPointsCount;
        
        if (confidenceValue >= 0.95 && rSquaredValue >= 0.8 && dataPoints >= 30) {
            return TrendQuality.EXCELLENT;
        } else if (confidenceValue >= 0.85 && rSquaredValue >= 0.6 && dataPoints >= 20) {
            return TrendQuality.GOOD;
        } else if (confidenceValue >= 0.70 && rSquaredValue >= 0.4 && dataPoints >= 10) {
            return TrendQuality.FAIR;
        } else {
            return TrendQuality.POOR;
        }
    }

    /**
     * Checks if the prediction interval is narrow (indicating high precision)
     * 
     * @return true if prediction interval is narrow
     */
    public boolean hasNarrowPredictionInterval() {
        if (predictedNextPeriod == null || predictionLowerBound == null || predictionUpperBound == null) {
            return false;
        }
        
        if (predictedNextPeriod.compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }
        
        BigDecimal intervalWidth = predictionUpperBound.subtract(predictionLowerBound);
        BigDecimal relativeWidth = intervalWidth.divide(predictedNextPeriod, 4, RoundingMode.HALF_UP);
        
        // Consider narrow if interval is less than 20% of the predicted value
        return relativeWidth.compareTo(BigDecimal.valueOf(0.20)) < 0;
    }

    /**
     * Validates the business rules for this trend data
     * 
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return analysisStartDate != null && analysisEndDate != null
            && analysisStartDate.isBefore(analysisEndDate)
            && (correlation == null || (correlation.compareTo(BigDecimal.valueOf(-1)) >= 0 && correlation.compareTo(BigDecimal.ONE) <= 0))
            && (rSquared == null || (rSquared.compareTo(BigDecimal.ZERO) >= 0 && rSquared.compareTo(BigDecimal.ONE) <= 0))
            && (confidence == null || (confidence.compareTo(BigDecimal.ZERO) >= 0 && confidence.compareTo(BigDecimal.ONE) <= 0));
    }

    /**
     * Creates a sanitized copy suitable for external APIs
     * 
     * @return sanitized copy without internal metadata
     */
    public SpendingTrend sanitizedCopy() {
        return this.toBuilder()
            .trendMetadata(null)
            .build();
    }

    /**
     * Trend direction enumeration
     */
    public enum TrendDirection {
        INCREASING, DECREASING, STABLE, VOLATILE, UNKNOWN
    }

    /**
     * Trend strength enumeration
     */
    public enum TrendStrength {
        WEAK, MODERATE, STRONG, UNKNOWN
    }

    /**
     * Seasonality level enumeration
     */
    public enum SeasonalityLevel {
        NONE, LOW, MODERATE, HIGH, UNKNOWN
    }

    /**
     * Volatility level enumeration
     */
    public enum VolatilityLevel {
        LOW, MODERATE, HIGH, UNKNOWN
    }

    /**
     * Trend quality enumeration
     */
    public enum TrendQuality {
        EXCELLENT, GOOD, FAIR, POOR, UNKNOWN
    }

    /**
     * Trend metadata inner class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Additional metadata for trend analysis")
    public static class TrendMetadata implements Serializable {
        
        @Serial
        private static final long serialVersionUID = 1L;

        @JsonProperty("algorithmVersion")
        @Schema(description = "Version of the trend analysis algorithm", example = "2.1.0")
        private String algorithmVersion;

        @JsonProperty("computationTime")
        @Schema(description = "Time taken to compute the trend in milliseconds", example = "250")
        private Long computationTime;

        @JsonProperty("outlierCount")
        @Schema(description = "Number of outliers detected and excluded", example = "2")
        private Integer outlierCount;

        @JsonProperty("adjustmentFactors")
        @Schema(description = "Any adjustment factors applied to the data")
        private String adjustmentFactors;

        @JsonProperty("dataQualityScore")
        @Schema(description = "Overall data quality score", example = "0.95")
        private BigDecimal dataQualityScore;

        @JsonProperty("modelParameters")
        @Schema(description = "Statistical model parameters used")
        private String modelParameters;

        @JsonProperty("crossValidationScore")
        @Schema(description = "Cross-validation score for model accuracy", example = "0.87")
        private BigDecimal crossValidationScore;

        @JsonProperty("lastUpdated")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        @Schema(description = "Last update timestamp", format = "date-time")
        private Instant lastUpdated;
    }

    /**
     * Custom equals method for better performance
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SpendingTrend that)) return false;
        return Objects.equals(trendId, that.trendId) && 
               Objects.equals(analysisStartDate, that.analysisStartDate) &&
               Objects.equals(analysisEndDate, that.analysisEndDate);
    }

    /**
     * Custom hashCode method for better performance
     */
    @Override
    public int hashCode() {
        return Objects.hash(trendId, analysisStartDate, analysisEndDate);
    }
}