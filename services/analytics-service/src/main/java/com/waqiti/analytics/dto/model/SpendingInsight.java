package com.waqiti.analytics.dto.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Spending Insight Data Transfer Object
 * 
 * Represents AI-generated insights with confidence scores and actionable recommendations.
 * This DTO follows Domain-Driven Design principles and includes comprehensive
 * validation, confidence scoring, and recommendation tracking capabilities.
 * 
 * <p>Used for:
 * <ul>
 *   <li>AI-powered spending analysis and insights</li>
 *   <li>Personalized financial recommendations</li>
 *   <li>Behavioral pattern recognition and advice</li>
 *   <li>Predictive analytics and future projections</li>
 *   <li>Financial wellness coaching</li>
 *   <li>Automated financial advisory services</li>
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
@ToString(exclude = {"insightMetadata"})
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(
    name = "SpendingInsight",
    description = "AI-generated spending insights with confidence scores and actionable recommendations",
    example = """
        {
          "insightId": "insight_12345",
          "userId": "user_67890",
          "insightType": "SPENDING_PATTERN",
          "title": "Increased Weekend Spending Detected",
          "description": "Your weekend spending has increased by 25% compared to last month",
          "confidence": 0.92,
          "severity": "MEDIUM",
          "category": "BEHAVIORAL_ANALYSIS",
          "actionable": true,
          "recommendations": ["Consider setting weekend spending limits"],
          "potentialSavings": 150.00,
          "currency": "USD",
          "version": "1.0"
        }
        """
)
public class SpendingInsight implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier for this insight
     */
    @NotBlank(message = "Insight ID cannot be blank")
    @JsonProperty("insightId")
    @JsonPropertyDescription("Unique identifier for this insight")
    @Schema(
        description = "Unique identifier for this insight",
        example = "insight_12345",
        required = true
    )
    private String insightId;

    /**
     * User ID this insight belongs to
     */
    @NotBlank(message = "User ID cannot be blank")
    @JsonProperty("userId")
    @JsonPropertyDescription("User ID this insight belongs to")
    @Schema(
        description = "User ID this insight belongs to",
        example = "user_67890",
        required = true
    )
    private String userId;

    /**
     * Type of insight
     */
    @NotNull(message = "Insight type cannot be null")
    @JsonProperty("insightType")
    @JsonPropertyDescription("Type of insight generated")
    @Schema(
        description = "Type of insight",
        example = "SPENDING_PATTERN",
        allowableValues = {"SPENDING_PATTERN", "BUDGET_OPTIMIZATION", "SAVINGS_OPPORTUNITY", "RISK_ALERT", "TREND_ANALYSIS", "CATEGORY_INSIGHT", "MERCHANT_INSIGHT", "SEASONAL_PATTERN"},
        required = true
    )
    private InsightType insightType;

    /**
     * Brief title for the insight
     */
    @NotBlank(message = "Title cannot be blank")
    @Size(min = 1, max = 150, message = "Title must be between 1 and 150 characters")
    @JsonProperty("title")
    @JsonPropertyDescription("Brief title for the insight")
    @Schema(
        description = "Brief title for the insight",
        example = "Increased Weekend Spending Detected",
        minLength = 1,
        maxLength = 150,
        required = true
    )
    private String title;

    /**
     * Detailed description of the insight
     */
    @NotBlank(message = "Description cannot be blank")
    @Size(min = 1, max = 1000, message = "Description must be between 1 and 1000 characters")
    @JsonProperty("description")
    @JsonPropertyDescription("Detailed description of the insight")
    @Schema(
        description = "Detailed description of the insight",
        example = "Your weekend spending has increased by 25% compared to last month",
        minLength = 1,
        maxLength = 1000,
        required = true
    )
    private String description;

    /**
     * AI confidence score for this insight
     */
    @NotNull(message = "Confidence cannot be null")
    @DecimalMin(value = "0.0", message = "Confidence must be between 0 and 1")
    @DecimalMax(value = "1.0", message = "Confidence must be between 0 and 1")
    @JsonProperty("confidence")
    @JsonPropertyDescription("AI confidence score for this insight (0-1)")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.####")
    @Schema(
        description = "AI confidence score for this insight",
        example = "0.92",
        minimum = "0",
        maximum = "1",
        required = true
    )
    private BigDecimal confidence;

    /**
     * Severity level of the insight
     */
    @JsonProperty("severity")
    @JsonPropertyDescription("Severity level of the insight (LOW, MEDIUM, HIGH, CRITICAL)")
    @Schema(
        description = "Severity level of the insight",
        example = "MEDIUM",
        allowableValues = {"LOW", "MEDIUM", "HIGH", "CRITICAL"}
    )
    private InsightSeverity severity;

    /**
     * Category of the insight
     */
    @JsonProperty("category")
    @JsonPropertyDescription("Category of the insight for grouping")
    @Schema(
        description = "Category of the insight",
        example = "BEHAVIORAL_ANALYSIS",
        allowableValues = {"BEHAVIORAL_ANALYSIS", "FINANCIAL_HEALTH", "BUDGET_MANAGEMENT", "SAVINGS_OPTIMIZATION", "RISK_MANAGEMENT", "TREND_DETECTION"}
    )
    private InsightCategory category;

    /**
     * Whether this insight is actionable
     */
    @JsonProperty("actionable")
    @JsonPropertyDescription("Whether this insight provides actionable recommendations")
    @Schema(
        description = "Whether this insight is actionable",
        example = "true"
    )
    private Boolean actionable;

    /**
     * List of actionable recommendations
     */
    @JsonProperty("recommendations")
    @JsonPropertyDescription("List of actionable recommendations based on this insight")
    @Schema(
        description = "List of actionable recommendations",
        example = "[\"Consider setting weekend spending limits\"]"
    )
    private List<String> recommendations;

    /**
     * Potential savings amount if recommendations are followed
     */
    @DecimalMin(value = "0.00", message = "Potential savings must be non-negative")
    @JsonProperty("potentialSavings")
    @JsonPropertyDescription("Potential savings amount if recommendations are followed")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Potential savings amount",
        example = "150.00",
        minimum = "0"
    )
    private BigDecimal potentialSavings;

    /**
     * Impact score of implementing recommendations
     */
    @DecimalMin(value = "0.0", message = "Impact score must be between 0 and 10")
    @DecimalMax(value = "10.0", message = "Impact score must be between 0 and 10")
    @JsonProperty("impactScore")
    @JsonPropertyDescription("Impact score of implementing recommendations (0-10)")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.#")
    @Schema(
        description = "Impact score of implementing recommendations",
        example = "7.5",
        minimum = "0",
        maximum = "10"
    )
    private BigDecimal impactScore;

    /**
     * Time frame for the insight analysis
     */
    @JsonProperty("timeFrame")
    @JsonPropertyDescription("Time frame for the insight analysis")
    @Schema(
        description = "Time frame for the insight analysis",
        example = "LAST_30_DAYS",
        allowableValues = {"LAST_7_DAYS", "LAST_30_DAYS", "LAST_90_DAYS", "LAST_YEAR", "YEAR_TO_DATE", "CUSTOM"}
    )
    private TimeFrame timeFrame;

    /**
     * Related spending categories
     */
    @JsonProperty("relatedCategories")
    @JsonPropertyDescription("Spending categories related to this insight")
    @Schema(
        description = "Related spending categories",
        example = "[\"Entertainment\", \"Dining\"]"
    )
    private List<String> relatedCategories;

    /**
     * Related merchants
     */
    @JsonProperty("relatedMerchants")
    @JsonPropertyDescription("Merchants related to this insight")
    @Schema(
        description = "Related merchants",
        example = "[\"Netflix\", \"Starbucks\"]"
    )
    private List<String> relatedMerchants;

    /**
     * Data points used for generating this insight
     */
    @JsonProperty("dataPointsCount")
    @JsonPropertyDescription("Number of data points used for generating this insight")
    @Schema(
        description = "Number of data points used",
        example = "150"
    )
    private Integer dataPointsCount;

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
     * When this insight was generated
     */
    @JsonProperty("generatedAt")
    @JsonPropertyDescription("When this insight was generated")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    @Schema(
        description = "When this insight was generated",
        format = "date-time"
    )
    private Instant generatedAt;

    /**
     * When this insight expires (becomes stale)
     */
    @JsonProperty("expiresAt")
    @JsonPropertyDescription("When this insight expires and should be refreshed")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    @Schema(
        description = "When this insight expires",
        format = "date-time"
    )
    private Instant expiresAt;

    /**
     * Whether the user has acknowledged this insight
     */
    @JsonProperty("acknowledged")
    @JsonPropertyDescription("Whether the user has acknowledged this insight")
    @Schema(
        description = "Whether the user has acknowledged this insight",
        example = "false"
    )
    private Boolean acknowledged;

    /**
     * User feedback on this insight
     */
    @JsonProperty("userFeedback")
    @JsonPropertyDescription("User feedback on the usefulness of this insight")
    @Schema(
        description = "User feedback",
        example = "HELPFUL",
        allowableValues = {"HELPFUL", "NOT_HELPFUL", "ALREADY_KNOWN", "INCORRECT"}
    )
    private UserFeedback userFeedback;

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
     * Insight metadata for additional information
     */
    @JsonProperty("insightMetadata")
    @JsonPropertyDescription("Additional metadata for insight generation")
    @Schema(description = "Additional metadata for insight generation")
    private InsightMetadata insightMetadata;

    /**
     * Checks if the insight is still valid (not expired)
     * 
     * @return true if the insight is still valid
     */
    public boolean isValid() {
        return expiresAt == null || Instant.now().isBefore(expiresAt);
    }

    /**
     * Checks if the insight is high confidence
     * 
     * @param threshold confidence threshold (e.g., 0.8 for 80%)
     * @return true if confidence exceeds threshold
     */
    public boolean isHighConfidence(double threshold) {
        return confidence != null && confidence.doubleValue() >= threshold;
    }

    /**
     * Checks if the insight has high impact
     * 
     * @return true if impact score is high (>= 7.0)
     */
    public boolean isHighImpact() {
        return impactScore != null && impactScore.doubleValue() >= 7.0;
    }

    /**
     * Gets the priority score based on confidence, impact, and severity
     * 
     * @return calculated priority score
     */
    public BigDecimal getPriorityScore() {
        if (confidence == null) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal score = confidence.multiply(BigDecimal.valueOf(100));
        
        if (impactScore != null) {
            score = score.add(impactScore.multiply(BigDecimal.valueOf(10)));
        }
        
        if (severity != null) {
            score = score.add(BigDecimal.valueOf(severity.getWeight()));
        }
        
        return score;
    }

    /**
     * Validates the business rules for this insight
     * 
     * @return true if valid, false otherwise
     */
    public boolean isValidInsight() {
        return insightId != null && !insightId.trim().isEmpty()
            && userId != null && !userId.trim().isEmpty()
            && title != null && !title.trim().isEmpty()
            && description != null && !description.trim().isEmpty()
            && insightType != null
            && confidence != null && confidence.doubleValue() >= 0.0 && confidence.doubleValue() <= 1.0;
    }

    /**
     * Creates a sanitized copy suitable for external APIs
     * 
     * @return sanitized copy without internal metadata
     */
    public SpendingInsight sanitizedCopy() {
        return this.toBuilder()
            .insightMetadata(null)
            .build();
    }

    /**
     * Insight type enumeration
     */
    public enum InsightType {
        SPENDING_PATTERN,
        BUDGET_OPTIMIZATION,
        SAVINGS_OPPORTUNITY,
        RISK_ALERT,
        TREND_ANALYSIS,
        CATEGORY_INSIGHT,
        MERCHANT_INSIGHT,
        SEASONAL_PATTERN
    }

    /**
     * Insight severity enumeration
     */
    public enum InsightSeverity {
        LOW(1), MEDIUM(3), HIGH(5), CRITICAL(10);
        
        private final int weight;
        
        InsightSeverity(int weight) {
            this.weight = weight;
        }
        
        public int getWeight() {
            return weight;
        }
    }

    /**
     * Insight category enumeration
     */
    public enum InsightCategory {
        BEHAVIORAL_ANALYSIS,
        FINANCIAL_HEALTH,
        BUDGET_MANAGEMENT,
        SAVINGS_OPTIMIZATION,
        RISK_MANAGEMENT,
        TREND_DETECTION
    }

    /**
     * Time frame enumeration
     */
    public enum TimeFrame {
        LAST_7_DAYS,
        LAST_30_DAYS,
        LAST_90_DAYS,
        LAST_YEAR,
        YEAR_TO_DATE,
        CUSTOM
    }

    /**
     * User feedback enumeration
     */
    public enum UserFeedback {
        HELPFUL,
        NOT_HELPFUL,
        ALREADY_KNOWN,
        INCORRECT
    }

    /**
     * Insight metadata inner class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Additional metadata for insight generation")
    public static class InsightMetadata implements Serializable {
        
        @Serial
        private static final long serialVersionUID = 1L;

        @JsonProperty("modelVersion")
        @Schema(description = "AI model version used", example = "v2.1.0")
        private String modelVersion;

        @JsonProperty("algorithmType")
        @Schema(description = "Algorithm type used for generation", example = "RANDOM_FOREST")
        private String algorithmType;

        @JsonProperty("computationTime")
        @Schema(description = "Time taken to generate insight in milliseconds", example = "150")
        private Long computationTime;

        @JsonProperty("dataQualityScore")
        @Schema(description = "Quality score of input data", example = "0.95")
        private BigDecimal dataQualityScore;

        @JsonProperty("featureImportance")
        @Schema(description = "Key features that influenced this insight")
        private List<String> featureImportance;

        @JsonProperty("crossValidationScore")
        @Schema(description = "Cross-validation score for model accuracy", example = "0.89")
        private BigDecimal crossValidationScore;

        @JsonProperty("trainingDataSize")
        @Schema(description = "Size of training data used", example = "10000")
        private Integer trainingDataSize;
    }

    /**
     * Custom equals method for better performance
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SpendingInsight that)) return false;
        return Objects.equals(insightId, that.insightId) && 
               Objects.equals(userId, that.userId);
    }

    /**
     * Custom hashCode method for better performance
     */
    @Override
    public int hashCode() {
        return Objects.hash(insightId, userId);
    }
}