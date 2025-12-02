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
 * CardLimit entity - Card limit configuration
 * Represents spending and transaction limits for cards
 *
 * Supports:
 * - Transaction amount limits
 * - Daily/weekly/monthly spending limits
 * - Transaction type specific limits
 * - Merchant category limits
 * - Temporary limit overrides
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Entity
@Table(name = "card_limit", indexes = {
    @Index(name = "idx_card_limit_id", columnList = "limit_id"),
    @Index(name = "idx_card_limit_card", columnList = "card_id"),
    @Index(name = "idx_card_limit_type", columnList = "limit_type"),
    @Index(name = "idx_card_limit_active", columnList = "is_active"),
    @Index(name = "idx_card_limit_deleted", columnList = "deleted_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CardLimit extends BaseAuditEntity {

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

    @Column(name = "card_id", nullable = false)
    @NotNull(message = "Card ID is required")
    private UUID cardId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", insertable = false, updatable = false)
    private Card card;

    @Column(name = "user_id", nullable = false)
    @NotNull(message = "User ID is required")
    private UUID userId;

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

    @Column(name = "is_temporary")
    @Builder.Default
    private Boolean isTemporary = false;

    @Column(name = "priority")
    @Min(1)
    @Max(100)
    @Builder.Default
    private Integer priority = 50;

    // ========================================================================
    // TRANSACTION LIMITS
    // ========================================================================

    @Column(name = "per_transaction_limit", precision = 18, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal perTransactionLimit;

    @Column(name = "daily_transaction_limit", precision = 18, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal dailyTransactionLimit;

    @Column(name = "weekly_transaction_limit", precision = 18, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal weeklyTransactionLimit;

    @Column(name = "monthly_transaction_limit", precision = 18, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal monthlyTransactionLimit;

    @Column(name = "currency_code", length = 3)
    @Size(min = 3, max = 3)
    private String currencyCode;

    // ========================================================================
    // COUNT LIMITS
    // ========================================================================

    @Column(name = "daily_transaction_count_limit")
    @Min(0)
    private Integer dailyTransactionCountLimit;

    @Column(name = "weekly_transaction_count_limit")
    @Min(0)
    private Integer weeklyTransactionCountLimit;

    @Column(name = "monthly_transaction_count_limit")
    @Min(0)
    private Integer monthlyTransactionCountLimit;

    // ========================================================================
    // TRANSACTION TYPE SPECIFIC
    // ========================================================================

    @Column(name = "transaction_type", length = 30)
    @Size(max = 30)
    private String transactionType;

    @Column(name = "atm_daily_limit", precision = 18, scale = 2)
    private BigDecimal atmDailyLimit;

    @Column(name = "atm_weekly_limit", precision = 18, scale = 2)
    private BigDecimal atmWeeklyLimit;

    @Column(name = "atm_per_transaction_limit", precision = 18, scale = 2)
    private BigDecimal atmPerTransactionLimit;

    @Column(name = "pos_daily_limit", precision = 18, scale = 2)
    private BigDecimal posDailyLimit;

    @Column(name = "pos_weekly_limit", precision = 18, scale = 2)
    private BigDecimal posWeeklyLimit;

    @Column(name = "online_daily_limit", precision = 18, scale = 2)
    private BigDecimal onlineDailyLimit;

    @Column(name = "online_weekly_limit", precision = 18, scale = 2)
    private BigDecimal onlineWeeklyLimit;

    @Column(name = "contactless_per_transaction_limit", precision = 18, scale = 2)
    private BigDecimal contactlessPerTransactionLimit;

    @Column(name = "contactless_daily_limit", precision = 18, scale = 2)
    private BigDecimal contactlessDailyLimit;

    // ========================================================================
    // CASH ADVANCE LIMITS
    // ========================================================================

    @Column(name = "cash_advance_limit", precision = 18, scale = 2)
    private BigDecimal cashAdvanceLimit;

    @Column(name = "cash_advance_daily_limit", precision = 18, scale = 2)
    private BigDecimal cashAdvanceDailyLimit;

    @Column(name = "cash_advance_monthly_limit", precision = 18, scale = 2)
    private BigDecimal cashAdvanceMonthlyLimit;

    // ========================================================================
    // INTERNATIONAL LIMITS
    // ========================================================================

    @Column(name = "international_daily_limit", precision = 18, scale = 2)
    private BigDecimal internationalDailyLimit;

    @Column(name = "international_weekly_limit", precision = 18, scale = 2)
    private BigDecimal internationalWeeklyLimit;

    @Column(name = "international_monthly_limit", precision = 18, scale = 2)
    private BigDecimal internationalMonthlyLimit;

    // ========================================================================
    // MERCHANT CATEGORY LIMITS
    // ========================================================================

    @Column(name = "merchant_category_code", length = 4)
    @Size(min = 4, max = 4)
    private String merchantCategoryCode;

    @Column(name = "mcc_daily_limit", precision = 18, scale = 2)
    private BigDecimal mccDailyLimit;

    @Column(name = "mcc_weekly_limit", precision = 18, scale = 2)
    private BigDecimal mccWeeklyLimit;

    @Column(name = "mcc_monthly_limit", precision = 18, scale = 2)
    private BigDecimal mccMonthlyLimit;

    // ========================================================================
    // USAGE TRACKING
    // ========================================================================

    @Column(name = "current_daily_amount", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal currentDailyAmount = BigDecimal.ZERO;

    @Column(name = "current_weekly_amount", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal currentWeeklyAmount = BigDecimal.ZERO;

    @Column(name = "current_monthly_amount", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal currentMonthlyAmount = BigDecimal.ZERO;

    @Column(name = "current_daily_count")
    @Builder.Default
    private Integer currentDailyCount = 0;

    @Column(name = "current_weekly_count")
    @Builder.Default
    private Integer currentWeeklyCount = 0;

    @Column(name = "current_monthly_count")
    @Builder.Default
    private Integer currentMonthlyCount = 0;

    @Column(name = "last_reset_date")
    private LocalDateTime lastResetDate;

    // ========================================================================
    // TEMPORARY OVERRIDE
    // ========================================================================

    @Column(name = "temporary_limit_amount", precision = 18, scale = 2)
    private BigDecimal temporaryLimitAmount;

    @Column(name = "temporary_limit_start")
    private LocalDateTime temporaryLimitStart;

    @Column(name = "temporary_limit_end")
    private LocalDateTime temporaryLimitEnd;

    @Column(name = "temporary_limit_reason", length = 255)
    @Size(max = 255)
    private String temporaryLimitReason;

    @Column(name = "temporary_limit_approved_by", length = 100)
    @Size(max = 100)
    private String temporaryLimitApprovedBy;

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Column(name = "effective_from")
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_until")
    private LocalDateTime effectiveUntil;

    @Column(name = "last_modified_by", length = 100)
    @Size(max = 100)
    private String lastModifiedBy;

    @Column(name = "modification_reason", length = 255)
    @Size(max = 255)
    private String modificationReason;

    // ========================================================================
    // METADATA
    // ========================================================================

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "limit_rules", columnDefinition = "jsonb")
    private Map<String, Object> limitRules;

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
     * Get effective limit (considers temporary override)
     */
    @Transient
    public BigDecimal getEffectiveLimit(String period) {
        // Check temporary override
        if (isTemporaryLimitActive()) {
            return temporaryLimitAmount;
        }

        // Return normal limit based on period
        switch (period.toUpperCase()) {
            case "TRANSACTION":
                return perTransactionLimit;
            case "DAILY":
                return dailyTransactionLimit;
            case "WEEKLY":
                return weeklyTransactionLimit;
            case "MONTHLY":
                return monthlyTransactionLimit;
            default:
                return null;
        }
    }

    /**
     * Check if temporary limit is active
     */
    @Transient
    public boolean isTemporaryLimitActive() {
        if (!isTemporary || temporaryLimitAmount == null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();

        if (temporaryLimitStart != null && now.isBefore(temporaryLimitStart)) {
            return false;
        }

        if (temporaryLimitEnd != null && now.isAfter(temporaryLimitEnd)) {
            return false;
        }

        return true;
    }

    /**
     * Check if transaction would exceed limit
     */
    @Transient
    public boolean wouldExceedLimit(BigDecimal amount, String period) {
        if (amount == null) {
            return false;
        }

        BigDecimal limit = getEffectiveLimit(period);
        if (limit == null) {
            return false;
        }

        BigDecimal current = getCurrentUsage(period);
        return current.add(amount).compareTo(limit) > 0;
    }

    /**
     * Get current usage for period
     */
    @Transient
    public BigDecimal getCurrentUsage(String period) {
        switch (period.toUpperCase()) {
            case "DAILY":
                return currentDailyAmount != null ? currentDailyAmount : BigDecimal.ZERO;
            case "WEEKLY":
                return currentWeeklyAmount != null ? currentWeeklyAmount : BigDecimal.ZERO;
            case "MONTHLY":
                return currentMonthlyAmount != null ? currentMonthlyAmount : BigDecimal.ZERO;
            default:
                return BigDecimal.ZERO;
        }
    }

    /**
     * Get remaining limit
     */
    @Transient
    public BigDecimal getRemainingLimit(String period) {
        BigDecimal limit = getEffectiveLimit(period);
        if (limit == null) {
            return null;
        }

        BigDecimal current = getCurrentUsage(period);
        BigDecimal remaining = limit.subtract(current);

        return remaining.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remaining;
    }

    /**
     * Increment usage
     */
    public void incrementUsage(BigDecimal amount) {
        if (amount == null) {
            return;
        }

        this.currentDailyAmount = this.currentDailyAmount.add(amount);
        this.currentWeeklyAmount = this.currentWeeklyAmount.add(amount);
        this.currentMonthlyAmount = this.currentMonthlyAmount.add(amount);

        this.currentDailyCount++;
        this.currentWeeklyCount++;
        this.currentMonthlyCount++;
    }

    /**
     * Reset usage for period
     */
    public void resetUsage(String period) {
        LocalDateTime now = LocalDateTime.now();

        switch (period.toUpperCase()) {
            case "DAILY":
                this.currentDailyAmount = BigDecimal.ZERO;
                this.currentDailyCount = 0;
                break;
            case "WEEKLY":
                this.currentWeeklyAmount = BigDecimal.ZERO;
                this.currentWeeklyCount = 0;
                break;
            case "MONTHLY":
                this.currentMonthlyAmount = BigDecimal.ZERO;
                this.currentMonthlyCount = 0;
                break;
            case "ALL":
                this.currentDailyAmount = BigDecimal.ZERO;
                this.currentWeeklyAmount = BigDecimal.ZERO;
                this.currentMonthlyAmount = BigDecimal.ZERO;
                this.currentDailyCount = 0;
                this.currentWeeklyCount = 0;
                this.currentMonthlyCount = 0;
                break;
        }

        this.lastResetDate = now;
    }

    /**
     * Set temporary limit
     */
    public void setTemporaryLimit(BigDecimal amount, LocalDateTime start, LocalDateTime end,
                                    String reason, String approvedBy) {
        this.isTemporary = true;
        this.temporaryLimitAmount = amount;
        this.temporaryLimitStart = start;
        this.temporaryLimitEnd = end;
        this.temporaryLimitReason = reason;
        this.temporaryLimitApprovedBy = approvedBy;
    }

    /**
     * Clear temporary limit
     */
    public void clearTemporaryLimit() {
        this.isTemporary = false;
        this.temporaryLimitAmount = null;
        this.temporaryLimitStart = null;
        this.temporaryLimitEnd = null;
        this.temporaryLimitReason = null;
        this.temporaryLimitApprovedBy = null;
    }

    /**
     * Update limit
     */
    public void updateLimit(BigDecimal newLimit, String period, String modifiedBy, String reason) {
        switch (period.toUpperCase()) {
            case "TRANSACTION":
                this.perTransactionLimit = newLimit;
                break;
            case "DAILY":
                this.dailyTransactionLimit = newLimit;
                break;
            case "WEEKLY":
                this.weeklyTransactionLimit = newLimit;
                break;
            case "MONTHLY":
                this.monthlyTransactionLimit = newLimit;
                break;
        }

        this.lastModifiedBy = modifiedBy;
        this.modificationReason = reason;
    }

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (isActive == null) {
            isActive = true;
        }
        if (isTemporary == null) {
            isTemporary = false;
        }
        if (priority == null) {
            priority = 50;
        }
        if (currentDailyAmount == null) {
            currentDailyAmount = BigDecimal.ZERO;
        }
        if (currentWeeklyAmount == null) {
            currentWeeklyAmount = BigDecimal.ZERO;
        }
        if (currentMonthlyAmount == null) {
            currentMonthlyAmount = BigDecimal.ZERO;
        }
        if (currentDailyCount == null) {
            currentDailyCount = 0;
        }
        if (currentWeeklyCount == null) {
            currentWeeklyCount = 0;
        }
        if (currentMonthlyCount == null) {
            currentMonthlyCount = 0;
        }
        if (effectiveFrom == null) {
            effectiveFrom = LocalDateTime.now();
        }
    }
}
