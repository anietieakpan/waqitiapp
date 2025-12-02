package com.waqiti.card.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * CardFraudRule entity - Fraud detection rule
 * Represents a configurable fraud detection rule
 *
 * Rules can be based on:
 * - Transaction amount thresholds
 * - Velocity (number of transactions in time period)
 * - Geographic location
 * - Merchant category
 * - Transaction patterns
 * - Device fingerprinting
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Entity
@Table(name = "card_fraud_rule", indexes = {
    @Index(name = "idx_fraud_rule_id", columnList = "rule_id"),
    @Index(name = "idx_fraud_rule_type", columnList = "rule_type"),
    @Index(name = "idx_fraud_rule_active", columnList = "is_active"),
    @Index(name = "idx_fraud_rule_priority", columnList = "priority"),
    @Index(name = "idx_fraud_rule_deleted", columnList = "deleted_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CardFraudRule extends BaseAuditEntity {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // ========================================================================
    // RULE IDENTIFICATION
    // ========================================================================

    @Column(name = "rule_id", unique = true, nullable = false, length = 100)
    @NotBlank(message = "Rule ID is required")
    private String ruleId;

    @Column(name = "rule_name", nullable = false, length = 255)
    @NotBlank(message = "Rule name is required")
    private String ruleName;

    @Column(name = "rule_description", columnDefinition = "TEXT")
    private String ruleDescription;

    // ========================================================================
    // RULE CONFIGURATION
    // ========================================================================

    @Column(name = "rule_type", nullable = false, length = 50)
    @NotBlank(message = "Rule type is required")
    private String ruleType;

    @Column(name = "rule_category", length = 50)
    @Size(max = 50)
    private String ruleCategory;

    @Column(name = "priority")
    @Min(1)
    @Max(100)
    @Builder.Default
    private Integer priority = 50;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_blocking")
    @Builder.Default
    private Boolean isBlocking = false;

    // ========================================================================
    // RULE CONDITIONS (JSONB)
    // ========================================================================

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conditions", columnDefinition = "jsonb", nullable = false)
    @NotNull
    private Map<String, Object> conditions;

    // ========================================================================
    // AMOUNT THRESHOLDS
    // ========================================================================

    @Column(name = "min_transaction_amount", precision = 18, scale = 2)
    private BigDecimal minTransactionAmount;

    @Column(name = "max_transaction_amount", precision = 18, scale = 2)
    private BigDecimal maxTransactionAmount;

    @Column(name = "daily_amount_threshold", precision = 18, scale = 2)
    private BigDecimal dailyAmountThreshold;

    @Column(name = "weekly_amount_threshold", precision = 18, scale = 2)
    private BigDecimal weeklyAmountThreshold;

    @Column(name = "monthly_amount_threshold", precision = 18, scale = 2)
    private BigDecimal monthlyAmountThreshold;

    // ========================================================================
    // VELOCITY LIMITS
    // ========================================================================

    @Column(name = "max_transactions_per_hour")
    @Min(0)
    private Integer maxTransactionsPerHour;

    @Column(name = "max_transactions_per_day")
    @Min(0)
    private Integer maxTransactionsPerDay;

    @Column(name = "max_transactions_per_week")
    @Min(0)
    private Integer maxTransactionsPerWeek;

    @Column(name = "max_transactions_per_month")
    @Min(0)
    private Integer maxTransactionsPerMonth;

    // ========================================================================
    // GEOGRAPHIC RESTRICTIONS
    // ========================================================================

    @ElementCollection
    @CollectionTable(name = "card_fraud_rule_allowed_countries", joinColumns = @JoinColumn(name = "rule_id"))
    @Column(name = "country_code")
    private java.util.List<String> allowedCountries;

    @ElementCollection
    @CollectionTable(name = "card_fraud_rule_blocked_countries", joinColumns = @JoinColumn(name = "rule_id"))
    @Column(name = "country_code")
    private java.util.List<String> blockedCountries;

    @Column(name = "block_international_transactions")
    @Builder.Default
    private Boolean blockInternationalTransactions = false;

    // ========================================================================
    // MERCHANT RESTRICTIONS
    // ========================================================================

    @ElementCollection
    @CollectionTable(name = "card_fraud_rule_blocked_mcc", joinColumns = @JoinColumn(name = "rule_id"))
    @Column(name = "mcc_code")
    private java.util.List<String> blockedMerchantCategories;

    @ElementCollection
    @CollectionTable(name = "card_fraud_rule_allowed_mcc", joinColumns = @JoinColumn(name = "rule_id"))
    @Column(name = "mcc_code")
    private java.util.List<String> allowedMerchantCategories;

    @ElementCollection
    @CollectionTable(name = "card_fraud_rule_blocked_merchants", joinColumns = @JoinColumn(name = "rule_id"))
    @Column(name = "merchant_id")
    private java.util.List<String> blockedMerchants;

    // ========================================================================
    // TIME RESTRICTIONS
    // ========================================================================

    @Column(name = "time_based_restriction")
    private Boolean timeBasedRestriction;

    @Column(name = "allowed_hours_start")
    @Min(0)
    @Max(23)
    private Integer allowedHoursStart;

    @Column(name = "allowed_hours_end")
    @Min(0)
    @Max(23)
    private Integer allowedHoursEnd;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_days_of_week", columnDefinition = "jsonb")
    private java.util.List<Integer> allowedDaysOfWeek;

    // ========================================================================
    // RISK SCORING
    // ========================================================================

    @Column(name = "risk_score_weight", precision = 5, scale = 2)
    @DecimalMin(value = "0.00")
    @DecimalMax(value = "100.00")
    @Builder.Default
    private BigDecimal riskScoreWeight = BigDecimal.ZERO;

    @Column(name = "risk_score_threshold", precision = 5, scale = 2)
    @DecimalMin(value = "0.00")
    @DecimalMax(value = "100.00")
    private BigDecimal riskScoreThreshold;

    // ========================================================================
    // ACTIONS
    // ========================================================================

    @Column(name = "action_on_trigger", length = 50)
    @Size(max = 50)
    private String actionOnTrigger;

    @Column(name = "alert_severity", length = 20)
    @Size(max = 20)
    private String alertSeverity;

    @Column(name = "notify_cardholder")
    @Builder.Default
    private Boolean notifyCardholder = false;

    @Column(name = "notify_fraud_team")
    @Builder.Default
    private Boolean notifyFraudTeam = true;

    @Column(name = "require_manual_review")
    @Builder.Default
    private Boolean requireManualReview = false;

    // ========================================================================
    // SCOPE
    // ========================================================================

    @Column(name = "applies_to_card_type", length = 20)
    @Size(max = 20)
    private String appliesToCardType;

    @Column(name = "applies_to_product_id", length = 100)
    @Size(max = 100)
    private String appliesToProductId;

    @Column(name = "applies_to_all_cards")
    @Builder.Default
    private Boolean appliesToAllCards = true;

    @ElementCollection
    @CollectionTable(name = "card_fraud_rule_specific_cards", joinColumns = @JoinColumn(name = "rule_id"))
    @Column(name = "card_id")
    private java.util.List<UUID> specificCardIds;

    // ========================================================================
    // STATISTICS
    // ========================================================================

    @Column(name = "trigger_count")
    @Builder.Default
    private Long triggerCount = 0L;

    @Column(name = "last_triggered_date")
    private LocalDateTime lastTriggeredDate;

    @Column(name = "true_positive_count")
    @Builder.Default
    private Long truePositiveCount = 0L;

    @Column(name = "false_positive_count")
    @Builder.Default
    private Long falsePositiveCount = 0L;

    @Column(name = "effectiveness_score", precision = 5, scale = 2)
    private BigDecimal effectivenessScore;

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Column(name = "effective_from")
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_until")
    private LocalDateTime effectiveUntil;

    @Column(name = "version")
    @Builder.Default
    private Integer version = 1;

    // ========================================================================
    // METADATA
    // ========================================================================

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Check if rule is currently effective
     */
    @Transient
    public boolean isEffective() {
        if (!isActive || deletedAt != null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();

        if (effectiveFrom != null && now.isBefore(effectiveFrom)) {
            return false;
        }

        if (effectiveUntil != null && now.isAfter(effectiveUntil)) {
            return false;
        }

        return true;
    }

    /**
     * Check if rule applies to specific card
     */
    @Transient
    public boolean appliesToCard(UUID cardId, String cardType, String productId) {
        if (!isEffective()) {
            return false;
        }

        if (appliesToAllCards) {
            // Check exclusions for card type and product
            if (appliesToCardType != null && !appliesToCardType.equals(cardType)) {
                return false;
            }
            if (appliesToProductId != null && !appliesToProductId.equals(productId)) {
                return false;
            }
            return true;
        }

        // Check specific card IDs
        return specificCardIds != null && specificCardIds.contains(cardId);
    }

    /**
     * Check if amount is within thresholds
     */
    @Transient
    public boolean isAmountWithinThreshold(BigDecimal amount) {
        if (amount == null) {
            return false;
        }

        if (minTransactionAmount != null && amount.compareTo(minTransactionAmount) < 0) {
            return false;
        }

        if (maxTransactionAmount != null && amount.compareTo(maxTransactionAmount) > 0) {
            return false;
        }

        return true;
    }

    /**
     * Check if country is allowed
     */
    @Transient
    public boolean isCountryAllowed(String countryCode) {
        if (blockInternationalTransactions && countryCode != null && !countryCode.equals("USA")) {
            return false;
        }

        if (blockedCountries != null && blockedCountries.contains(countryCode)) {
            return false;
        }

        if (allowedCountries != null && !allowedCountries.isEmpty()) {
            return allowedCountries.contains(countryCode);
        }

        return true;
    }

    /**
     * Check if merchant category is allowed
     */
    @Transient
    public boolean isMerchantCategoryAllowed(String mcc) {
        if (blockedMerchantCategories != null && blockedMerchantCategories.contains(mcc)) {
            return false;
        }

        if (allowedMerchantCategories != null && !allowedMerchantCategories.isEmpty()) {
            return allowedMerchantCategories.contains(mcc);
        }

        return true;
    }

    /**
     * Check if merchant is blocked
     */
    @Transient
    public boolean isMerchantBlocked(String merchantId) {
        return blockedMerchants != null && blockedMerchants.contains(merchantId);
    }

    /**
     * Increment trigger count
     */
    public void recordTrigger() {
        this.triggerCount = (this.triggerCount == null ? 0L : this.triggerCount) + 1;
        this.lastTriggeredDate = LocalDateTime.now();
    }

    /**
     * Record true positive (confirmed fraud)
     */
    public void recordTruePositive() {
        this.truePositiveCount = (this.truePositiveCount == null ? 0L : this.truePositiveCount) + 1;
        calculateEffectiveness();
    }

    /**
     * Record false positive (legitimate transaction flagged)
     */
    public void recordFalsePositive() {
        this.falsePositiveCount = (this.falsePositiveCount == null ? 0L : this.falsePositiveCount) + 1;
        calculateEffectiveness();
    }

    /**
     * Calculate effectiveness score (precision)
     */
    private void calculateEffectiveness() {
        long total = (truePositiveCount == null ? 0L : truePositiveCount) +
                     (falsePositiveCount == null ? 0L : falsePositiveCount);

        if (total == 0) {
            this.effectivenessScore = BigDecimal.ZERO;
            return;
        }

        BigDecimal truePos = new BigDecimal(truePositiveCount == null ? 0L : truePositiveCount);
        BigDecimal totalBD = new BigDecimal(total);

        this.effectivenessScore = truePos.divide(totalBD, 4, java.math.RoundingMode.HALF_UP)
                                         .multiply(new BigDecimal("100"));
    }

    /**
     * Deactivate rule
     */
    public void deactivate(String reason) {
        this.isActive = false;
        this.notes = (this.notes != null ? this.notes + "\n" : "") +
                     "DEACTIVATED: " + reason + " at " + LocalDateTime.now();
    }

    /**
     * Activate rule
     */
    public void activate() {
        this.isActive = true;
        if (effectiveFrom == null) {
            this.effectiveFrom = LocalDateTime.now();
        }
    }

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (isActive == null) {
            isActive = true;
        }
        if (isBlocking == null) {
            isBlocking = false;
        }
        if (priority == null) {
            priority = 50;
        }
        if (appliesToAllCards == null) {
            appliesToAllCards = true;
        }
        if (notifyCardholder == null) {
            notifyCardholder = false;
        }
        if (notifyFraudTeam == null) {
            notifyFraudTeam = true;
        }
        if (requireManualReview == null) {
            requireManualReview = false;
        }
        if (triggerCount == null) {
            triggerCount = 0L;
        }
        if (truePositiveCount == null) {
            truePositiveCount = 0L;
        }
        if (falsePositiveCount == null) {
            falsePositiveCount = 0L;
        }
        if (version == null) {
            version = 1;
        }
        if (effectiveFrom == null) {
            effectiveFrom = LocalDateTime.now();
        }
    }
}
