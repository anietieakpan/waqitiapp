package com.waqiti.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * DTO for cash deposit limits including daily, monthly, and yearly restrictions.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashDepositLimitsDto {

    /**
     * User ID these limits apply to.
     */
    @NotBlank(message = "User ID cannot be blank")
    @JsonProperty("user_id")
    private String userId;

    /**
     * Network provider these limits apply to.
     */
    @Pattern(regexp = "^(MONEYGRAM|WESTERN_UNION|INGO_MONEY|GREEN_DOT|ALL)$", 
             message = "Network provider must be one of: MONEYGRAM, WESTERN_UNION, INGO_MONEY, GREEN_DOT, ALL")
    @JsonProperty("network_provider")
    private String networkProvider;

    /**
     * Daily deposit limits.
     */
    @NotNull(message = "Daily limits cannot be null")
    @JsonProperty("daily_limits")
    private PeriodLimits dailyLimits;

    /**
     * Monthly deposit limits.
     */
    @NotNull(message = "Monthly limits cannot be null")
    @JsonProperty("monthly_limits")
    private PeriodLimits monthlyLimits;

    /**
     * Yearly deposit limits.
     */
    @NotNull(message = "Yearly limits cannot be null")
    @JsonProperty("yearly_limits")
    private PeriodLimits yearlyLimits;

    /**
     * Per-transaction limits.
     */
    @JsonProperty("transaction_limits")
    private TransactionLimits transactionLimits;

    /**
     * Account-level limits based on verification status.
     */
    @JsonProperty("account_limits")
    private AccountLimits accountLimits;

    /**
     * Current usage tracking.
     */
    @JsonProperty("current_usage")
    private UsageTracking currentUsage;

    /**
     * Currency for all limit amounts.
     */
    @Builder.Default
    private String currency = "USD";

    /**
     * When these limits were last updated.
     */
    @JsonProperty("last_updated")
    private LocalDate lastUpdated;

    /**
     * Period-based limits (daily, monthly, yearly).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodLimits {
        
        /**
         * Maximum amount that can be deposited in this period.
         */
        @Positive(message = "Maximum amount must be positive")
        @JsonProperty("max_amount")
        private BigDecimal maxAmount;
        
        /**
         * Maximum number of transactions in this period.
         */
        @Positive(message = "Maximum count must be positive")
        @JsonProperty("max_count")
        private Integer maxCount;
        
        /**
         * Amount already used in current period.
         */
        @JsonProperty("used_amount")
        @Builder.Default
        private BigDecimal usedAmount = BigDecimal.ZERO;
        
        /**
         * Number of transactions already made in current period.
         */
        @JsonProperty("used_count")
        @Builder.Default
        private Integer usedCount = 0;
        
        /**
         * Remaining amount available in this period.
         */
        @JsonProperty("remaining_amount")
        private BigDecimal remainingAmount;
        
        /**
         * Remaining transaction count in this period.
         */
        @JsonProperty("remaining_count")
        private Integer remainingCount;
        
        /**
         * When this period resets.
         */
        @JsonProperty("reset_date")
        private LocalDate resetDate;
    }

    /**
     * Per-transaction limits.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionLimits {
        
        /**
         * Minimum amount per transaction.
         */
        @JsonProperty("min_amount")
        private BigDecimal minAmount;
        
        /**
         * Maximum amount per transaction.
         */
        @JsonProperty("max_amount")
        private BigDecimal maxAmount;
        
        /**
         * Maximum number of transactions per day.
         */
        @JsonProperty("max_transactions_per_day")
        private Integer maxTransactionsPerDay;
        
        /**
         * Cooling off period between transactions (in minutes).
         */
        @JsonProperty("cooloff_period_minutes")
        private Integer cooloffPeriodMinutes;
    }

    /**
     * Account-level limits based on verification and risk profile.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountLimits {
        
        /**
         * User's verification level.
         */
        @JsonProperty("verification_level")
        private String verificationLevel;
        
        /**
         * Risk assessment level.
         */
        @JsonProperty("risk_level")
        private String riskLevel;
        
        /**
         * Lifetime maximum deposit amount.
         */
        @JsonProperty("lifetime_max_amount")
        private BigDecimal lifetimeMaxAmount;
        
        /**
         * Lifetime maximum transaction count.
         */
        @JsonProperty("lifetime_max_count")
        private Integer lifetimeMaxCount;
        
        /**
         * Account age in days.
         */
        @JsonProperty("account_age_days")
        private Integer accountAgeDays;
        
        /**
         * Whether the account is in good standing.
         */
        @JsonProperty("good_standing")
        @Builder.Default
        private Boolean goodStanding = true;
    }

    /**
     * Current usage tracking across all periods.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsageTracking {
        
        /**
         * Total amount deposited today.
         */
        @JsonProperty("today_amount")
        @Builder.Default
        private BigDecimal todayAmount = BigDecimal.ZERO;
        
        /**
         * Number of transactions today.
         */
        @JsonProperty("today_count")
        @Builder.Default
        private Integer todayCount = 0;
        
        /**
         * Total amount deposited this month.
         */
        @JsonProperty("month_amount")
        @Builder.Default
        private BigDecimal monthAmount = BigDecimal.ZERO;
        
        /**
         * Number of transactions this month.
         */
        @JsonProperty("month_count")
        @Builder.Default
        private Integer monthCount = 0;
        
        /**
         * Total amount deposited this year.
         */
        @JsonProperty("year_amount")
        @Builder.Default
        private BigDecimal yearAmount = BigDecimal.ZERO;
        
        /**
         * Number of transactions this year.
         */
        @JsonProperty("year_count")
        @Builder.Default
        private Integer yearCount = 0;
        
        /**
         * Timestamp of last transaction.
         */
        @JsonProperty("last_transaction_time")
        private java.time.LocalDateTime lastTransactionTime;
    }

    /**
     * Checks if a deposit amount is within all applicable limits.
     *
     * @param amount the deposit amount to check
     * @return true if within limits
     */
    public boolean isWithinLimits(BigDecimal amount) {
        return isWithinDailyLimits(amount) &&
               isWithinMonthlyLimits(amount) &&
               isWithinYearlyLimits(amount) &&
               isWithinTransactionLimits(amount);
    }

    /**
     * Checks if amount is within daily limits.
     */
    public boolean isWithinDailyLimits(BigDecimal amount) {
        if (dailyLimits == null || amount == null) {
            return false;
        }
        
        BigDecimal newTotal = dailyLimits.usedAmount.add(amount);
        return newTotal.compareTo(dailyLimits.maxAmount) <= 0 &&
               (dailyLimits.usedCount + 1) <= dailyLimits.maxCount;
    }

    /**
     * Checks if amount is within monthly limits.
     */
    public boolean isWithinMonthlyLimits(BigDecimal amount) {
        if (monthlyLimits == null || amount == null) {
            return false;
        }
        
        BigDecimal newTotal = monthlyLimits.usedAmount.add(amount);
        return newTotal.compareTo(monthlyLimits.maxAmount) <= 0 &&
               (monthlyLimits.usedCount + 1) <= monthlyLimits.maxCount;
    }

    /**
     * Checks if amount is within yearly limits.
     */
    public boolean isWithinYearlyLimits(BigDecimal amount) {
        if (yearlyLimits == null || amount == null) {
            return false;
        }
        
        BigDecimal newTotal = yearlyLimits.usedAmount.add(amount);
        return newTotal.compareTo(yearlyLimits.maxAmount) <= 0 &&
               (yearlyLimits.usedCount + 1) <= yearlyLimits.maxCount;
    }

    /**
     * Checks if amount is within per-transaction limits.
     */
    public boolean isWithinTransactionLimits(BigDecimal amount) {
        if (transactionLimits == null || amount == null) {
            return true; // No transaction limits set
        }
        
        return (transactionLimits.minAmount == null || amount.compareTo(transactionLimits.minAmount) >= 0) &&
               (transactionLimits.maxAmount == null || amount.compareTo(transactionLimits.maxAmount) <= 0);
    }

    /**
     * Gets the maximum amount that can still be deposited today.
     */
    public BigDecimal getAvailableDailyAmount() {
        if (dailyLimits == null) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal remaining = dailyLimits.maxAmount.subtract(dailyLimits.usedAmount);
        return remaining.max(BigDecimal.ZERO);
    }

    /**
     * Gets the maximum amount that can still be deposited this month.
     */
    public BigDecimal getAvailableMonthlyAmount() {
        if (monthlyLimits == null) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal remaining = monthlyLimits.maxAmount.subtract(monthlyLimits.usedAmount);
        return remaining.max(BigDecimal.ZERO);
    }

    /**
     * Gets the maximum amount that can be deposited considering all limits.
     */
    public BigDecimal getMaxAvailableAmount() {
        BigDecimal dailyAvailable = getAvailableDailyAmount();
        BigDecimal monthlyAvailable = getAvailableMonthlyAmount();
        BigDecimal yearlyAvailable = yearlyLimits != null ? 
            yearlyLimits.maxAmount.subtract(yearlyLimits.usedAmount) : BigDecimal.ZERO;
        
        BigDecimal available = dailyAvailable;
        if (monthlyAvailable.compareTo(available) < 0) {
            available = monthlyAvailable;
        }
        if (yearlyAvailable.compareTo(available) < 0) {
            available = yearlyAvailable;
        }
        
        // Also consider transaction limits
        if (transactionLimits != null && transactionLimits.maxAmount != null) {
            available = available.min(transactionLimits.maxAmount);
        }
        
        return available.max(BigDecimal.ZERO);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CashDepositLimitsDto that = (CashDepositLimitsDto) o;
        return Objects.equals(userId, that.userId) &&
               Objects.equals(networkProvider, that.networkProvider);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, networkProvider);
    }

    @Override
    public String toString() {
        return "CashDepositLimitsDto{" +
               "userId='" + userId + '\'' +
               ", networkProvider='" + networkProvider + '\'' +
               ", dailyMax=" + (dailyLimits != null ? dailyLimits.maxAmount : null) +
               ", monthlyMax=" + (monthlyLimits != null ? monthlyLimits.maxAmount : null) +
               ", currency='" + currency + '\'' +
               '}';
    }
}