package com.waqiti.risk.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Velocity Data Entity
 *
 * Tracks transaction velocity and patterns including:
 * - Transaction frequency
 * - Amount velocity
 * - Pattern detection
 * - Anomaly tracking
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "velocity_data")
@CompoundIndexes({
    @CompoundIndex(name = "user_window_idx", def = "{'userId': 1, 'windowStart': 1}"),
    @CompoundIndex(name = "merchant_window_idx", def = "{'merchantId': 1, 'windowStart': 1}")
})
public class VelocityData {

    @Id
    private String id;

    @Indexed
    @NotBlank
    private String userId;

    @Indexed
    private String merchantId;

    @Indexed
    private String deviceId;

    @Indexed
    private String ipAddress;

    // Time Window
    @Indexed
    private LocalDateTime windowStart;

    @Indexed
    private LocalDateTime windowEnd;

    private String windowType; // MINUTE, HOUR, DAY, WEEK

    private Integer windowDurationMinutes;

    // Transaction Counts
    @Builder.Default
    private Integer transactionCount = 0;

    @Builder.Default
    private Integer successfulTransactions = 0;

    @Builder.Default
    private Integer failedTransactions = 0;

    @Builder.Default
    private Integer blockedTransactions = 0;

    @Builder.Default
    private Integer declinedTransactions = 0;

    // Amount Metrics
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    private BigDecimal averageAmount;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private BigDecimal medianAmount;

    // Unique Entities
    @Builder.Default
    private Integer uniqueMerchants = 0;

    @Builder.Default
    private Integer uniqueCountries = 0;

    @Builder.Default
    private Integer uniqueDevices = 0;

    @Builder.Default
    private Integer uniqueIpAddresses = 0;

    private List<String> merchantIds;
    private List<String> countryList;

    // Temporal Metrics
    private Long averageTimeBetweenTransactionsSec;
    private Long minTimeBetweenTransactionsSec;
    private Long maxTimeBetweenTransactionsSec;

    private LocalDateTime firstTransactionAt;
    private LocalDateTime lastTransactionAt;

    // Velocity Scores
    private Double velocityScore; // 0-1, higher = more suspicious

    // Pattern Detection
    @Builder.Default
    private Boolean cardTestingPatternDetected = false;

    @Builder.Default
    private Boolean rapidFireDetected = false;

    @Builder.Default
    private Boolean unusualPatternDetected = false;

    private List<String> detectedPatterns;

    // Comparison to Baseline
    private Double deviationFromNormal; // Standard deviations

    private Integer percentileRank; // 0-100, compared to user's history

    // Risk Indicators
    @Builder.Default
    private Boolean exceedsThreshold = false;

    private List<String> exceededThresholds;

    // Geographic Velocity
    @Builder.Default
    private Boolean multipleCountries = false;

    @Builder.Default
    private Integer distinctCountries = 0;

    private String primaryCountry;

    // Merchant Velocity
    @Builder.Default
    private Boolean multipleNewMerchants = false;

    private Integer newMerchantCount;

    // Device Velocity
    @Builder.Default
    private Boolean multipleDevices = false;

    // Payment Method Velocity
    private Map<String, Integer> paymentMethodCounts;

    @Builder.Default
    private Integer distinctPaymentMethods = 0;

    // Failure Patterns
    private Double failureRate;

    @Builder.Default
    private Boolean highFailureRate = false;

    private List<String> failureReasons;

    // Amount Patterns
    @Builder.Default
    private Boolean smallAmountPattern = false; // Card testing

    @Builder.Default
    private Boolean largeAmountPattern = false; // Unusual large amounts

    @Builder.Default
    private Boolean incrementalAmountPattern = false; // Testing limits

    // Session Patterns
    private String sessionId;

    @Builder.Default
    private Integer transactionsInSession = 0;

    // Metadata
    private Map<String, Object> metadata;

    // Audit Fields
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    /**
     * Calculate velocity score
     */
    public void calculateVelocityScore() {
        double score = 0.0;

        // Transaction count factor
        if (transactionCount != null && transactionCount > 10) {
            score += 0.3;
        } else if (transactionCount > 5) {
            score += 0.1;
        }

        // Unique merchants factor
        if (uniqueMerchants != null && uniqueMerchants > 5) {
            score += 0.2;
        }

        // Failure rate factor
        if (failureRate != null && failureRate > 0.3) {
            score += 0.2;
        }

        // Pattern detection factor
        if (cardTestingPatternDetected) {
            score += 0.4;
        }
        if (rapidFireDetected) {
            score += 0.3;
        }

        // Multiple countries factor
        if (multipleCountries && distinctCountries > 2) {
            score += 0.3;
        }

        // Deviation from normal
        if (deviationFromNormal != null && deviationFromNormal > 2.0) {
            score += 0.2;
        }

        this.velocityScore = Math.min(1.0, score);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Update failure rate
     */
    public void updateFailureRate() {
        int total = successfulTransactions + failedTransactions + declinedTransactions;
        if (total > 0) {
            this.failureRate = (double) (failedTransactions + declinedTransactions) / total;
            this.highFailureRate = failureRate > 0.3;
        }
    }

    /**
     * Add transaction to velocity window
     */
    public void addTransaction(BigDecimal amount, boolean successful, boolean blocked) {
        this.transactionCount++;

        if (blocked) {
            this.blockedTransactions++;
        } else if (successful) {
            this.successfulTransactions++;
        } else {
            this.failedTransactions++;
        }

        // Update amount totals
        if (totalAmount == null) {
            totalAmount = BigDecimal.ZERO;
        }
        this.totalAmount = totalAmount.add(amount);

        // Update min/max
        if (minAmount == null || amount.compareTo(minAmount) < 0) {
            this.minAmount = amount;
        }
        if (maxAmount == null || amount.compareTo(maxAmount) > 0) {
            this.maxAmount = amount;
        }

        // Recalculate average
        if (transactionCount > 0) {
            this.averageAmount = totalAmount.divide(
                new BigDecimal(transactionCount),
                2,
                java.math.RoundingMode.HALF_UP
            );
        }

        updateFailureRate();
        this.lastTransactionAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Detect card testing pattern
     */
    public void detectCardTestingPattern() {
        // Small amounts in rapid succession
        if (transactionCount >= 5 &&
            averageAmount != null &&
            averageAmount.compareTo(new BigDecimal("10")) < 0 &&
            averageTimeBetweenTransactionsSec != null &&
            averageTimeBetweenTransactionsSec < 60) {

            this.cardTestingPatternDetected = true;

            if (detectedPatterns == null) {
                detectedPatterns = new java.util.ArrayList<>();
            }
            if (!detectedPatterns.contains("CARD_TESTING")) {
                detectedPatterns.add("CARD_TESTING");
            }
        }
    }

    /**
     * Detect rapid fire pattern
     */
    public void detectRapidFirePattern() {
        if (transactionCount >= 3 &&
            averageTimeBetweenTransactionsSec != null &&
            averageTimeBetweenTransactionsSec < 10) {

            this.rapidFireDetected = true;

            if (detectedPatterns == null) {
                detectedPatterns = new java.util.ArrayList<>();
            }
            if (!detectedPatterns.contains("RAPID_FIRE")) {
                detectedPatterns.add("RAPID_FIRE");
            }
        }
    }

    /**
     * Check if velocity is suspicious
     */
    public boolean isSuspicious() {
        return (velocityScore != null && velocityScore > 0.6) ||
               cardTestingPatternDetected ||
               rapidFireDetected ||
               (highFailureRate != null && highFailureRate) ||
               exceedsThreshold;
    }
}
