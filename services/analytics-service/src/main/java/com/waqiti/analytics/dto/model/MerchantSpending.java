package com.waqiti.analytics.dto.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
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
import java.time.LocalDate;
import java.util.Objects;

/**
 * Merchant Spending Data Transfer Object
 * 
 * Represents merchant-level spending analysis with loyalty metrics and behavior patterns.
 * This DTO follows Domain-Driven Design principles and includes comprehensive
 * validation, frequency analysis, and loyalty tracking capabilities.
 * 
 * <p>Used for:
 * <ul>
 *   <li>Merchant-specific spending analysis</li>
 *   <li>Customer loyalty and frequency tracking</li>
 *   <li>Merchant preference identification</li>
 *   <li>Spending concentration analysis</li>
 *   <li>Cashback and rewards optimization</li>
 *   <li>Merchant recommendation systems</li>
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
@ToString(exclude = {"merchantMetadata"})
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(
    name = "MerchantSpending",
    description = "Merchant-level spending analysis with loyalty metrics and frequency patterns",
    example = """
        {
          "merchantId": "merchant_12345",
          "merchantName": "Starbucks",
          "merchantCategory": "Coffee Shops",
          "totalAmount": 425.75,
          "transactionCount": 28,
          "averageTransactionAmount": 15.21,
          "frequencyScore": 8.5,
          "loyaltyTier": "GOLD",
          "firstTransactionDate": "2023-06-15",
          "lastTransactionDate": "2024-01-28",
          "currency": "USD",
          "version": "1.0"
        }
        """
)
public class MerchantSpending implements Serializable, Comparable<MerchantSpending> {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier for the merchant
     */
    @NotBlank(message = "Merchant ID cannot be blank")
    @JsonProperty("merchantId")
    @JsonPropertyDescription("Unique identifier for the merchant")
    @Schema(
        description = "Unique identifier for the merchant",
        example = "merchant_12345",
        required = true
    )
    private String merchantId;

    /**
     * Display name of the merchant
     */
    @NotBlank(message = "Merchant name cannot be blank")
    @Size(min = 1, max = 200, message = "Merchant name must be between 1 and 200 characters")
    @JsonProperty("merchantName")
    @JsonPropertyDescription("Display name of the merchant")
    @Schema(
        description = "Display name of the merchant",
        example = "Starbucks",
        minLength = 1,
        maxLength = 200,
        required = true
    )
    private String merchantName;

    /**
     * Category classification of the merchant
     */
    @Size(max = 100, message = "Merchant category must not exceed 100 characters")
    @JsonProperty("merchantCategory")
    @JsonPropertyDescription("Category classification of the merchant")
    @Schema(
        description = "Category classification of the merchant",
        example = "Coffee Shops",
        maxLength = 100
    )
    private String merchantCategory;

    /**
     * Merchant Category Code (MCC)
     */
    @JsonProperty("merchantCategoryCode")
    @JsonPropertyDescription("Merchant Category Code (MCC) for industry classification")
    @Schema(
        description = "Merchant Category Code (MCC)",
        example = "5814"
    )
    private String merchantCategoryCode;

    /**
     * Total spending amount at this merchant
     */
    @NotNull(message = "Amount cannot be null")
    @DecimalMin(value = "0.00", message = "Amount must be non-negative")
    @JsonProperty("totalAmount")
    @JsonPropertyDescription("Total spending amount at this merchant")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Total spending amount at this merchant",
        example = "425.75",
        minimum = "0",
        required = true
    )
    private BigDecimal totalAmount;

    /**
     * Number of transactions at this merchant
     */
    @Min(value = 0, message = "Transaction count must be non-negative")
    @JsonProperty("transactionCount")
    @JsonPropertyDescription("Number of transactions at this merchant")
    @Schema(
        description = "Number of transactions at this merchant",
        example = "28",
        minimum = "0",
        required = true
    )
    private long transactionCount;

    /**
     * Average amount per transaction
     */
    @DecimalMin(value = "0.00", message = "Average transaction amount must be non-negative")
    @JsonProperty("averageTransactionAmount")
    @JsonPropertyDescription("Average amount per transaction")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Average amount per transaction",
        example = "15.21",
        minimum = "0"
    )
    private BigDecimal averageTransactionAmount;

    /**
     * Frequency score indicating how often the user visits this merchant
     */
    @DecimalMin(value = "0.0", message = "Frequency score must be non-negative")
    @JsonProperty("frequencyScore")
    @JsonPropertyDescription("Frequency score indicating visit pattern (0-10 scale)")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.#")
    @Schema(
        description = "Frequency score (0-10 scale)",
        example = "8.5",
        minimum = "0",
        maximum = "10"
    )
    private BigDecimal frequencyScore;

    /**
     * Loyalty tier classification
     */
    @JsonProperty("loyaltyTier")
    @JsonPropertyDescription("Loyalty tier classification based on spending and frequency")
    @Schema(
        description = "Loyalty tier classification",
        example = "GOLD",
        allowableValues = {"BRONZE", "SILVER", "GOLD", "PLATINUM", "DIAMOND"}
    )
    private LoyaltyTier loyaltyTier;

    /**
     * Date of first transaction with this merchant
     */
    @JsonProperty("firstTransactionDate")
    @JsonPropertyDescription("Date of first transaction with this merchant")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @Schema(
        description = "Date of first transaction with this merchant",
        example = "2023-06-15",
        format = "date"
    )
    private LocalDate firstTransactionDate;

    /**
     * Date of last transaction with this merchant
     */
    @JsonProperty("lastTransactionDate")
    @JsonPropertyDescription("Date of last transaction with this merchant")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @Schema(
        description = "Date of last transaction with this merchant",
        example = "2024-01-28",
        format = "date"
    )
    private LocalDate lastTransactionDate;

    /**
     * Number of days since last transaction
     */
    @JsonProperty("daysSinceLastTransaction")
    @JsonPropertyDescription("Number of days since last transaction")
    @Schema(
        description = "Number of days since last transaction",
        example = "5"
    )
    private Integer daysSinceLastTransaction;

    /**
     * Customer lifetime value at this merchant
     */
    @DecimalMin(value = "0.00", message = "Customer lifetime value must be non-negative")
    @JsonProperty("customerLifetimeValue")
    @JsonPropertyDescription("Estimated customer lifetime value at this merchant")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Estimated customer lifetime value",
        example = "1250.00",
        minimum = "0"
    )
    private BigDecimal customerLifetimeValue;

    /**
     * Percentage of total spending allocated to this merchant
     */
    @DecimalMin(value = "0.0", message = "Spending percentage must be non-negative")
    @JsonProperty("spendingPercentage")
    @JsonPropertyDescription("Percentage of total spending allocated to this merchant")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Percentage of total spending",
        example = "12.5",
        minimum = "0",
        maximum = "100"
    )
    private BigDecimal spendingPercentage;

    /**
     * Monthly spending trend (positive for increasing, negative for decreasing)
     */
    @JsonProperty("monthlyTrend")
    @JsonPropertyDescription("Monthly spending trend percentage")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Monthly spending trend percentage",
        example = "5.2"
    )
    private BigDecimal monthlyTrend;

    /**
     * Seasonal pattern classification
     */
    @JsonProperty("seasonalPattern")
    @JsonPropertyDescription("Seasonal spending pattern classification")
    @Schema(
        description = "Seasonal spending pattern",
        example = "CONSISTENT",
        allowableValues = {"CONSISTENT", "SEASONAL", "HOLIDAY_DRIVEN", "IRREGULAR"}
    )
    private SeasonalPattern seasonalPattern;

    /**
     * Cashback or rewards earned
     */
    @DecimalMin(value = "0.00", message = "Rewards earned must be non-negative")
    @JsonProperty("rewardsEarned")
    @JsonPropertyDescription("Total cashback or rewards earned at this merchant")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Total rewards earned",
        example = "25.50",
        minimum = "0"
    )
    private BigDecimal rewardsEarned;

    /**
     * Effective rewards rate as a percentage
     */
    @DecimalMin(value = "0.0", message = "Rewards rate must be non-negative")
    @JsonProperty("rewardsRate")
    @JsonPropertyDescription("Effective rewards rate as a percentage")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Effective rewards rate percentage",
        example = "6.0",
        minimum = "0"
    )
    private BigDecimal rewardsRate;

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
     * Merchant metadata for additional information
     */
    @JsonProperty("merchantMetadata")
    @JsonPropertyDescription("Additional metadata for merchant analysis")
    @Schema(description = "Additional metadata for merchant analysis")
    private MerchantMetadata merchantMetadata;

    /**
     * Calculates the average transaction amount with defensive programming
     * 
     * @return calculated average or zero if no transactions
     */
    public BigDecimal calculateAverageTransactionAmount() {
        if (transactionCount == 0 || totalAmount == null) {
            return BigDecimal.ZERO;
        }
        return totalAmount.divide(BigDecimal.valueOf(transactionCount), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates the customer relationship duration in days
     * 
     * @return number of days between first and last transaction
     */
    public long getCustomerRelationshipDays() {
        if (firstTransactionDate == null || lastTransactionDate == null) {
            return 0;
        }
        return firstTransactionDate.until(lastTransactionDate).getDays();
    }

    /**
     * Determines if this is a favorite merchant based on frequency and spending
     * 
     * @param frequencyThreshold minimum frequency score for favorite status
     * @param spendingThreshold minimum spending percentage for favorite status
     * @return true if merchant qualifies as favorite
     */
    public boolean isFavoriteMerchant(double frequencyThreshold, double spendingThreshold) {
        boolean highFrequency = frequencyScore != null && frequencyScore.doubleValue() >= frequencyThreshold;
        boolean highSpending = spendingPercentage != null && spendingPercentage.doubleValue() >= spendingThreshold;
        return highFrequency || highSpending;
    }

    /**
     * Calculates the merchant loyalty score based on multiple factors
     * 
     * @return loyalty score (0-100)
     */
    public BigDecimal calculateLoyaltyScore() {
        BigDecimal score = BigDecimal.ZERO;
        
        // Frequency component (40%)
        if (frequencyScore != null) {
            score = score.add(frequencyScore.multiply(BigDecimal.valueOf(4)));
        }
        
        // Spending percentage component (30%)
        if (spendingPercentage != null) {
            score = score.add(spendingPercentage.multiply(BigDecimal.valueOf(0.3)));
        }
        
        // Relationship duration component (20%)
        long relationshipDays = getCustomerRelationshipDays();
        if (relationshipDays > 0) {
            BigDecimal durationScore = BigDecimal.valueOf(Math.min(relationshipDays / 365.0 * 20, 20));
            score = score.add(durationScore);
        }
        
        // Consistency component (10%)
        if (seasonalPattern == SeasonalPattern.CONSISTENT) {
            score = score.add(BigDecimal.valueOf(10));
        } else if (seasonalPattern == SeasonalPattern.SEASONAL) {
            score = score.add(BigDecimal.valueOf(5));
        }
        
        return score.min(BigDecimal.valueOf(100));
    }

    /**
     * Determines the recommended loyalty tier based on spending and frequency
     * 
     * @return recommended loyalty tier
     */
    public LoyaltyTier calculateRecommendedLoyaltyTier() {
        BigDecimal loyaltyScore = calculateLoyaltyScore();
        double score = loyaltyScore.doubleValue();
        
        if (score >= 80) {
            return LoyaltyTier.DIAMOND;
        } else if (score >= 65) {
            return LoyaltyTier.PLATINUM;
        } else if (score >= 50) {
            return LoyaltyTier.GOLD;
        } else if (score >= 30) {
            return LoyaltyTier.SILVER;
        } else {
            return LoyaltyTier.BRONZE;
        }
    }

    /**
     * Checks if the merchant relationship is at risk (inactive customer)
     * 
     * @param inactivityThresholdDays number of days to consider inactive
     * @return true if customer is at risk of churning
     */
    public boolean isAtRisk(int inactivityThresholdDays) {
        return daysSinceLastTransaction != null && daysSinceLastTransaction > inactivityThresholdDays;
    }

    /**
     * Validates the business rules for this merchant spending data
     * 
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return merchantId != null && !merchantId.trim().isEmpty()
            && merchantName != null && !merchantName.trim().isEmpty()
            && totalAmount != null && totalAmount.compareTo(BigDecimal.ZERO) >= 0
            && (averageTransactionAmount == null || averageTransactionAmount.compareTo(BigDecimal.ZERO) >= 0);
    }

    /**
     * Creates a sanitized copy suitable for external APIs
     * 
     * @return sanitized copy without internal metadata
     */
    public MerchantSpending sanitizedCopy() {
        return this.toBuilder()
            .merchantMetadata(null)
            .build();
    }

    /**
     * Natural ordering by total amount (descending)
     */
    @Override
    public int compareTo(MerchantSpending other) {
        if (other == null || other.totalAmount == null) {
            return this.totalAmount == null ? 0 : 1;
        }
        if (this.totalAmount == null) {
            return -1;
        }
        return other.totalAmount.compareTo(this.totalAmount); // Descending order
    }

    /**
     * Loyalty tier enumeration
     */
    public enum LoyaltyTier {
        BRONZE, SILVER, GOLD, PLATINUM, DIAMOND
    }

    /**
     * Seasonal pattern enumeration
     */
    public enum SeasonalPattern {
        CONSISTENT, SEASONAL, HOLIDAY_DRIVEN, IRREGULAR
    }

    /**
     * Merchant metadata inner class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Additional metadata for merchant analysis")
    public static class MerchantMetadata implements Serializable {
        
        @Serial
        private static final long serialVersionUID = 1L;

        @JsonProperty("merchantLocation")
        @Schema(description = "Primary merchant location", example = "New York, NY")
        private String merchantLocation;

        @JsonProperty("isOnlineOnly")
        @Schema(description = "Whether this is an online-only merchant", example = "false")
        private Boolean isOnlineOnly;

        @JsonProperty("chainBrand")
        @Schema(description = "Parent chain or brand name", example = "Starbucks Corporation")
        private String chainBrand;

        @JsonProperty("merchantRating")
        @Schema(description = "User rating for this merchant", example = "4.5")
        private BigDecimal merchantRating;

        @JsonProperty("preferredPaymentMethod")
        @Schema(description = "Most frequently used payment method", example = "Credit Card")
        private String preferredPaymentMethod;

        @JsonProperty("analysisStartDate")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        @Schema(description = "Start date of analysis period", format = "date")
        private LocalDate analysisStartDate;

        @JsonProperty("analysisEndDate")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        @Schema(description = "End date of analysis period", format = "date")
        private LocalDate analysisEndDate;

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
        if (!(o instanceof MerchantSpending that)) return false;
        return Objects.equals(merchantId, that.merchantId) && 
               Objects.equals(merchantName, that.merchantName);
    }

    /**
     * Custom hashCode method for better performance
     */
    @Override
    public int hashCode() {
        return Objects.hash(merchantId, merchantName);
    }
}