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
 * CardVelocityLimit entity - Velocity limit configuration
 * Represents velocity limits for card transactions
 *
 * Velocity limits control:
 * - Number of transactions per time period
 * - Transaction amount per time period
 * - Specific transaction types
 * - Geographic locations
 * - Merchant categories
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Entity
@Table(name = "card_velocity_limit", indexes = {
    @Index(name = "idx_velocity_limit_id", columnList = "limit_id"),
    @Index(name = "idx_velocity_card", columnList = "card_id"),
    @Index(name = "idx_velocity_user", columnList = "user_id"),
    @Index(name = "idx_velocity_type", columnList = "limit_type"),
    @Index(name = "idx_velocity_active", columnList = "is_active"),
    @Index(name = "idx_velocity_deleted", columnList = "deleted_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CardVelocityLimit extends BaseAuditEntity {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // ========================================================================
    // LIMIT IDENTIFICATION
    // ========================================================================

    @Column(name = "limit_id", unique = true, nullable = false, length = 100)
    @NotBlank(message = "Limit ID is required")
    private String limitId;

    @Column(name = "limit_name", length = 255)
    @Size(max = 255)
    private String limitName;

    @Column(name = "limit_description", columnDefinition = "TEXT")
    private String limitDescription;

    // ========================================================================
    // REFERENCES
    // ========================================================================

    @Column(name = "card_id")
    private UUID cardId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "product_id", length = 100)
    @Size(max = 100)
    private String productId;

    @Column(name = "applies_to_all_cards")
    @Builder.Default
    private Boolean appliesToAllCards = false;

    // ========================================================================
    // LIMIT CONFIGURATION
    // ========================================================================

    @Column(name = "limit_type", nullable = false, length = 50)
    @NotBlank(message = "Limit type is required")
    private String limitType;

    @Column(name = "limit_scope", length = 50)
    @Size(max = 50)
    private String limitScope;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "priority")
    @Min(1)
    @Max(100)
    @Builder.Default
    private Integer priority = 50;

    // ========================================================================
    // TRANSACTION COUNT LIMITS
    // ========================================================================

    @Column(name = "max_transactions_per_minute")
    @Min(0)
    private Integer maxTransactionsPerMinute;

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
    // AMOUNT LIMITS
    // ========================================================================

    @Column(name = "max_amount_per_transaction", precision = 18, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal maxAmountPerTransaction;

    @Column(name = "max_amount_per_hour", precision = 18, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal maxAmountPerHour;

    @Column(name = "max_amount_per_day", precision = 18, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal maxAmountPerDay;

    @Column(name = "max_amount_per_week", precision = 18, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal maxAmountPerWeek;

    @Column(name = "max_amount_per_month", precision = 18, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal maxAmountPerMonth;

    @Column(name = "currency_code", length = 3)
    @Size(min = 3, max = 3)
    private String currencyCode;

    // ========================================================================
    // TRANSACTION TYPE RESTRICTIONS
    // ========================================================================

    @ElementCollection
    @CollectionTable(name = "card_velocity_limit_transaction_types", joinColumns = @JoinColumn(name = "limit_id"))
    @Column(name = "transaction_type")
    private java.util.List<String> applicableTransactionTypes;

    @Column(name = "applies_to_all_transaction_types")
    @Builder.Default
    private Boolean appliesToAllTransactionTypes = true;

    // ========================================================================
    // GEOGRAPHIC RESTRICTIONS
    // ========================================================================

    @ElementCollection
    @CollectionTable(name = "card_velocity_limit_countries", joinColumns = @JoinColumn(name = "limit_id"))
    @Column(name = "country_code")
    private java.util.List<String> applicableCountries;

    @Column(name = "applies_to_all_countries")
    @Builder.Default
    private Boolean appliesToAllCountries = true;

    @Column(name = "domestic_only")
    @Builder.Default
    private Boolean domesticOnly = false;

    @Column(name = "international_only")
    @Builder.Default
    private Boolean internationalOnly = false;

    // ========================================================================
    // MERCHANT RESTRICTIONS
    // ========================================================================

    @ElementCollection
    @CollectionTable(name = "card_velocity_limit_mcc", joinColumns = @JoinColumn(name = "limit_id"))
    @Column(name = "mcc_code")
    private java.util.List<String> applicableMerchantCategories;

    @Column(name = "applies_to_all_merchants")
    @Builder.Default
    private Boolean appliesToAllMerchants = true;

    // ========================================================================
    // CHANNEL RESTRICTIONS
    // ========================================================================

    @Column(name = "online_transactions")
    private Boolean onlineTransactions;

    @Column(name = "offline_transactions")
    private Boolean offlineTransactions;

    @Column(name = "atm_transactions")
    private Boolean atmTransactions;

    @Column(name = "pos_transactions")
    private Boolean posTransactions;

    @Column(name = "contactless_transactions")
    private Boolean contactlessTransactions;

    // ========================================================================
    // TIME WINDOWS
    // ========================================================================

    @Column(name = "rolling_window_enabled")
    @Builder.Default
    private Boolean rollingWindowEnabled = true;

    @Column(name = "rolling_window_minutes")
    @Min(1)
    private Integer rollingWindowMinutes;

    @Column(name = "reset_at_midnight")
    @Builder.Default
    private Boolean resetAtMidnight = false;

    // ========================================================================
    // BREACH ACTIONS
    // ========================================================================

    @Column(name = "action_on_breach", length = 50)
    @Size(max = 50)
    private String actionOnBreach;

    @Column(name = "block_transaction_on_breach")
    @Builder.Default
    private Boolean blockTransactionOnBreach = true;

    @Column(name = "alert_on_breach")
    @Builder.Default
    private Boolean alertOnBreach = true;

    @Column(name = "notify_user_on_breach")
    @Builder.Default
    private Boolean notifyUserOnBreach = false;

    @Column(name = "lock_card_on_breach")
    @Builder.Default
    private Boolean lockCardOnBreach = false;

    // ========================================================================
    // USAGE TRACKING
    // ========================================================================

    @Column(name = "current_count_minute")
    @Builder.Default
    private Integer currentCountMinute = 0;

    @Column(name = "current_count_hour")
    @Builder.Default
    private Integer currentCountHour = 0;

    @Column(name = "current_count_day")
    @Builder.Default
    private Integer currentCountDay = 0;

    @Column(name = "current_count_week")
    @Builder.Default
    private Integer currentCountWeek = 0;

    @Column(name = "current_count_month")
    @Builder.Default
    private Integer currentCountMonth = 0;

    @Column(name = "current_amount_hour", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal currentAmountHour = BigDecimal.ZERO;

    @Column(name = "current_amount_day", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal currentAmountDay = BigDecimal.ZERO;

    @Column(name = "current_amount_week", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal currentAmountWeek = BigDecimal.ZERO;

    @Column(name = "current_amount_month", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal currentAmountMonth = BigDecimal.ZERO;

    @Column(name = "last_transaction_date")
    private LocalDateTime lastTransactionDate;

    @Column(name = "last_reset_date")
    private LocalDateTime lastResetDate;

    // ========================================================================
    // BREACH TRACKING
    // ========================================================================

    @Column(name = "total_breaches")
    @Builder.Default
    private Long totalBreaches = 0L;

    @Column(name = "last_breach_date")
    private LocalDateTime lastBreachDate;

    @Column(name = "breach_details", columnDefinition = "TEXT")
    private String breachDetails;

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Column(name = "effective_from")
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_until")
    private LocalDateTime effectiveUntil;

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
     * Check if limit is currently effective
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
     * Check if transaction count would breach limit
     */
    @Transient
    public boolean wouldBreachCountLimit(String period) {
        switch (period.toUpperCase()) {
            case "MINUTE":
                return maxTransactionsPerMinute != null &&
                       currentCountMinute >= maxTransactionsPerMinute;
            case "HOUR":
                return maxTransactionsPerHour != null &&
                       currentCountHour >= maxTransactionsPerHour;
            case "DAY":
                return maxTransactionsPerDay != null &&
                       currentCountDay >= maxTransactionsPerDay;
            case "WEEK":
                return maxTransactionsPerWeek != null &&
                       currentCountWeek >= maxTransactionsPerWeek;
            case "MONTH":
                return maxTransactionsPerMonth != null &&
                       currentCountMonth >= maxTransactionsPerMonth;
            default:
                return false;
        }
    }

    /**
     * Check if transaction amount would breach limit
     */
    @Transient
    public boolean wouldBreachAmountLimit(BigDecimal amount, String period) {
        if (amount == null) {
            return false;
        }

        switch (period.toUpperCase()) {
            case "TRANSACTION":
                return maxAmountPerTransaction != null &&
                       amount.compareTo(maxAmountPerTransaction) > 0;
            case "HOUR":
                return maxAmountPerHour != null &&
                       currentAmountHour.add(amount).compareTo(maxAmountPerHour) > 0;
            case "DAY":
                return maxAmountPerDay != null &&
                       currentAmountDay.add(amount).compareTo(maxAmountPerDay) > 0;
            case "WEEK":
                return maxAmountPerWeek != null &&
                       currentAmountWeek.add(amount).compareTo(maxAmountPerWeek) > 0;
            case "MONTH":
                return maxAmountPerMonth != null &&
                       currentAmountMonth.add(amount).compareTo(maxAmountPerMonth) > 0;
            default:
                return false;
        }
    }

    /**
     * Increment transaction count
     */
    public void incrementTransactionCount(BigDecimal amount) {
        this.currentCountMinute++;
        this.currentCountHour++;
        this.currentCountDay++;
        this.currentCountWeek++;
        this.currentCountMonth++;

        if (amount != null) {
            this.currentAmountHour = this.currentAmountHour.add(amount);
            this.currentAmountDay = this.currentAmountDay.add(amount);
            this.currentAmountWeek = this.currentAmountWeek.add(amount);
            this.currentAmountMonth = this.currentAmountMonth.add(amount);
        }

        this.lastTransactionDate = LocalDateTime.now();
    }

    /**
     * Record breach
     */
    public void recordBreach(String details) {
        this.totalBreaches = (this.totalBreaches == null ? 0L : this.totalBreaches) + 1;
        this.lastBreachDate = LocalDateTime.now();
        this.breachDetails = details;
    }

    /**
     * Reset counters for specified period
     */
    public void resetCounters(String period) {
        LocalDateTime now = LocalDateTime.now();

        switch (period.toUpperCase()) {
            case "MINUTE":
                this.currentCountMinute = 0;
                break;
            case "HOUR":
                this.currentCountHour = 0;
                this.currentAmountHour = BigDecimal.ZERO;
                break;
            case "DAY":
                this.currentCountDay = 0;
                this.currentAmountDay = BigDecimal.ZERO;
                break;
            case "WEEK":
                this.currentCountWeek = 0;
                this.currentAmountWeek = BigDecimal.ZERO;
                break;
            case "MONTH":
                this.currentCountMonth = 0;
                this.currentAmountMonth = BigDecimal.ZERO;
                break;
            case "ALL":
                this.currentCountMinute = 0;
                this.currentCountHour = 0;
                this.currentCountDay = 0;
                this.currentCountWeek = 0;
                this.currentCountMonth = 0;
                this.currentAmountHour = BigDecimal.ZERO;
                this.currentAmountDay = BigDecimal.ZERO;
                this.currentAmountWeek = BigDecimal.ZERO;
                this.currentAmountMonth = BigDecimal.ZERO;
                break;
        }

        this.lastResetDate = now;
    }

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (isActive == null) {
            isActive = true;
        }
        if (priority == null) {
            priority = 50;
        }
        if (appliesToAllCards == null) {
            appliesToAllCards = false;
        }
        if (appliesToAllTransactionTypes == null) {
            appliesToAllTransactionTypes = true;
        }
        if (appliesToAllCountries == null) {
            appliesToAllCountries = true;
        }
        if (appliesToAllMerchants == null) {
            appliesToAllMerchants = true;
        }
        if (domesticOnly == null) {
            domesticOnly = false;
        }
        if (internationalOnly == null) {
            internationalOnly = false;
        }
        if (rollingWindowEnabled == null) {
            rollingWindowEnabled = true;
        }
        if (resetAtMidnight == null) {
            resetAtMidnight = false;
        }
        if (blockTransactionOnBreach == null) {
            blockTransactionOnBreach = true;
        }
        if (alertOnBreach == null) {
            alertOnBreach = true;
        }
        if (notifyUserOnBreach == null) {
            notifyUserOnBreach = false;
        }
        if (lockCardOnBreach == null) {
            lockCardOnBreach = false;
        }
        if (currentCountMinute == null) {
            currentCountMinute = 0;
        }
        if (currentCountHour == null) {
            currentCountHour = 0;
        }
        if (currentCountDay == null) {
            currentCountDay = 0;
        }
        if (currentCountWeek == null) {
            currentCountWeek = 0;
        }
        if (currentCountMonth == null) {
            currentCountMonth = 0;
        }
        if (currentAmountHour == null) {
            currentAmountHour = BigDecimal.ZERO;
        }
        if (currentAmountDay == null) {
            currentAmountDay = BigDecimal.ZERO;
        }
        if (currentAmountWeek == null) {
            currentAmountWeek = BigDecimal.ZERO;
        }
        if (currentAmountMonth == null) {
            currentAmountMonth = BigDecimal.ZERO;
        }
        if (totalBreaches == null) {
            totalBreaches = 0L;
        }
        if (effectiveFrom == null) {
            effectiveFrom = LocalDateTime.now();
        }
    }
}
