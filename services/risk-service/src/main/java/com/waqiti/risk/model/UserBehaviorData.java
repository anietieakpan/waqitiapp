package com.waqiti.risk.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User Behavior Data Entity
 *
 * Captures detailed behavioral patterns for users including:
 * - Transaction patterns
 * - Session behavior
 * - Navigation patterns
 * - Temporal patterns
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_behavior_data")
public class UserBehaviorData {

    @Id
    private String id;

    @Indexed
    @NotBlank
    private String userId;

    @Indexed
    private LocalDateTime periodStart;

    @Indexed
    private LocalDateTime periodEnd;

    private String aggregationPeriod; // HOURLY, DAILY, WEEKLY, MONTHLY

    // Transaction Patterns
    private Integer totalTransactions;
    private BigDecimal totalAmount;
    private BigDecimal averageAmount;
    private BigDecimal medianAmount;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private BigDecimal stdDevAmount;

    // Temporal Patterns
    private Set<Integer> typicalTransactionHours; // 0-23
    private Set<String> typicalTransactionDays; // MON, TUE, etc.
    private Map<Integer, Integer> hourlyTransactionDistribution; // hour -> count
    private Map<String, Integer> dailyTransactionDistribution; // day -> count

    private Integer mostActiveHour;
    private String mostActiveDay;

    // Session Patterns
    private Integer totalSessions;
    private Long averageSessionDurationSec;
    private Long medianSessionDurationSec;
    private Long minSessionDurationSec;
    private Long maxSessionDurationSec;

    private Double averagePagesPerSession;
    private Integer averageClicksPerSession;

    // Navigation Patterns
    private List<String> commonNavigationPaths;
    private Map<String, Integer> pageVisitCounts;
    private Double directCheckoutRate; // Percentage of direct checkouts

    // Merchant Patterns
    private Set<String> typicalMerchantCategories;
    private Set<String> frequentMerchants;
    private Integer uniqueMerchantsCount;
    private Map<String, Integer> merchantFrequency;

    // Device Patterns
    private Set<String> usedDevices;
    private String primaryDevice;
    private Map<String, Integer> deviceUsageCount;
    private Boolean multipleDeviceUser;

    // Geographic Patterns
    private Set<String> visitedCountries;
    private String primaryCountry;
    private Set<String> visitedCities;
    private Boolean frequentTraveler;

    // Velocity Patterns
    private Double averageTimeBetweenTransactionsSec;
    private Integer maxTransactionsPerHour;
    private Integer maxTransactionsPerDay;

    // Payment Patterns
    private Map<String, Integer> paymentMethodUsage;
    private String preferredPaymentMethod;

    // Risk Indicators
    private Integer failedTransactionAttempts;
    private Integer canceledTransactions;
    private Integer chargebackCount;
    private Integer refundCount;

    private Set<String> unusualBehaviors; // List of detected anomalies

    // Behavioral Scores
    private Double consistencyScore; // How consistent is the behavior (0-1)
    private Double predictabilityScore; // How predictable (0-1)
    private Double anomalyScore; // Current anomaly level (0-1)

    // Comparison to Baseline
    private Double deviationFromBaseline; // Standard deviations from normal

    // Account Activity
    private LocalDateTime firstTransactionAt;
    private LocalDateTime lastTransactionAt;
    private Integer daysActive;
    private Integer daysSinceLastTransaction;

    // Social/Network Behavior
    private Integer uniqueRecipients;
    private Set<String> frequentRecipients;
    private Boolean hasRepeatedRecipients;

    // Login Patterns
    private Integer loginCount;
    private Set<Integer> typicalLoginHours;
    private Set<String> loginCountries;

    // Feature Vector for ML
    private Map<String, Double> behavioralFeatures;

    // Audit Fields
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    private LocalDateTime lastAnalyzedAt;

    // Metadata
    private Map<String, Object> metadata;

    /**
     * Calculate behavior consistency score
     */
    public void calculateConsistencyScore() {
        double score = 1.0;

        // Reduce score based on various factors
        if (unusualBehaviors != null && !unusualBehaviors.isEmpty()) {
            score -= (unusualBehaviors.size() * 0.1);
        }

        if (deviationFromBaseline != null && deviationFromBaseline > 2.0) {
            score -= 0.3;
        }

        this.consistencyScore = Math.max(0.0, Math.min(1.0, score));
    }

    /**
     * Check if behavior is unusual
     */
    public boolean isUnusualBehavior() {
        return (anomalyScore != null && anomalyScore > 0.6) ||
               (deviationFromBaseline != null && deviationFromBaseline > 3.0) ||
               (consistencyScore != null && consistencyScore < 0.4);
    }

    /**
     * Add unusual behavior flag
     */
    public void addUnusualBehavior(String behavior) {
        if (unusualBehaviors == null) {
            unusualBehaviors = new java.util.HashSet<>();
        }
        unusualBehaviors.add(behavior);
        this.updatedAt = LocalDateTime.now();
    }
}
