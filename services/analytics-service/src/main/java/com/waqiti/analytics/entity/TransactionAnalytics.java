package com.waqiti.analytics.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transaction Analytics Entity
 *
 * Stores aggregated transaction analytics data for various time periods.
 * Supports hourly, daily, weekly, monthly, quarterly, and yearly aggregations.
 *
 * Features:
 * - Volume metrics (transaction counts by status)
 * - Amount metrics (total, average, median, min, max)
 * - Fee metrics (total fees, average fee)
 * - Performance metrics (success rate, processing time)
 * - Geographic metrics (country distribution)
 * - Device metrics (mobile, web, API)
 * - Risk metrics (fraud alerts, AML alerts)
 * - Growth metrics (growth rate, period-over-period)
 *
 * Optimistic Locking: @Version ensures concurrent update safety
 * Indexes: Composite indexes on user_id, merchant_id, analysis_date for performance
 *
 * @author Waqiti Platform Team
 * @version 1.0.0-PRODUCTION
 * @since 2025-11-10
 */
@Entity
@Table(name = "transaction_analytics", indexes = {
    @Index(name = "idx_transaction_analytics_user_date", columnList = "user_id, analysis_date"),
    @Index(name = "idx_transaction_analytics_merchant_date", columnList = "merchant_id, analysis_date"),
    @Index(name = "idx_transaction_analytics_date_period", columnList = "analysis_date, period_type"),
    @Index(name = "idx_transaction_analytics_category", columnList = "transaction_category"),
    @Index(name = "idx_transaction_analytics_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"createdBy", "updatedBy"})
@EqualsAndHashCode(of = "id")
public class TransactionAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(name = "analysis_date", nullable = false)
    private LocalDateTime analysisDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", length = 20, nullable = false)
    private PeriodType periodType;

    @Column(name = "transaction_category", length = 100)
    private String transactionCategory;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    // ==================== VOLUME METRICS ====================

    @Column(name = "transaction_count", nullable = false)
    @Builder.Default
    private Long transactionCount = 0L;

    @Column(name = "successful_transactions", nullable = false)
    @Builder.Default
    private Long successfulTransactions = 0L;

    @Column(name = "failed_transactions", nullable = false)
    @Builder.Default
    private Long failedTransactions = 0L;

    @Column(name = "pending_transactions", nullable = false)
    @Builder.Default
    private Long pendingTransactions = 0L;

    // ==================== AMOUNT METRICS ====================

    @Column(name = "total_amount", precision = 19, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "successful_amount", precision = 19, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal successfulAmount = BigDecimal.ZERO;

    @Column(name = "failed_amount", precision = 19, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal failedAmount = BigDecimal.ZERO;

    @Column(name = "average_amount", precision = 19, scale = 4)
    private BigDecimal averageAmount;

    @Column(name = "median_amount", precision = 19, scale = 4)
    private BigDecimal medianAmount;

    @Column(name = "min_amount", precision = 19, scale = 4)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 19, scale = 4)
    private BigDecimal maxAmount;

    // ==================== FEE METRICS ====================

    @Column(name = "total_fees", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal totalFees = BigDecimal.ZERO;

    @Column(name = "average_fee", precision = 19, scale = 4)
    private BigDecimal averageFee;

    // ==================== PERFORMANCE METRICS ====================

    @Column(name = "success_rate", precision = 5, scale = 4)
    private BigDecimal successRate;

    @Column(name = "average_processing_time_ms")
    private Long averageProcessingTimeMs;

    @Column(name = "median_processing_time_ms")
    private Long medianProcessingTimeMs;

    // ==================== GEOGRAPHIC METRICS ====================

    @Column(name = "unique_countries")
    private Integer uniqueCountries;

    @Column(name = "top_country", length = 3)
    private String topCountry;

    @Column(name = "domestic_transactions")
    private Long domesticTransactions;

    @Column(name = "international_transactions")
    private Long internationalTransactions;

    // ==================== DEVICE AND CHANNEL METRICS ====================

    @Column(name = "unique_devices")
    private Integer uniqueDevices;

    @Column(name = "mobile_transactions")
    private Long mobileTransactions;

    @Column(name = "web_transactions")
    private Long webTransactions;

    @Column(name = "api_transactions")
    private Long apiTransactions;

    // ==================== RISK AND SECURITY METRICS ====================

    @Column(name = "high_risk_transactions")
    private Long highRiskTransactions;

    @Column(name = "fraud_alerts")
    private Long fraudAlerts;

    @Column(name = "aml_alerts")
    private Long amlAlerts;

    @Column(name = "blocked_transactions")
    private Long blockedTransactions;

    // ==================== TIME PATTERN METRICS ====================

    @Column(name = "peak_hour")
    private Integer peakHour;

    @Column(name = "peak_day", length = 10)
    private String peakDay;

    @Column(name = "business_hours_transactions")
    private Long businessHoursTransactions;

    @Column(name = "off_hours_transactions")
    private Long offHoursTransactions;

    // ==================== GROWTH METRICS ====================

    @Column(name = "growth_rate", precision = 5, scale = 4)
    private BigDecimal growthRate;

    @Column(name = "period_over_period_change", precision = 5, scale = 4)
    private BigDecimal periodOverPeriodChange;

    // ==================== ADDITIONAL METRICS ====================

    @Column(name = "unique_users")
    private Integer uniqueUsers;

    @Column(name = "repeat_users")
    private Integer repeatUsers;

    @Column(name = "new_users")
    private Integer newUsers;

    @Column(name = "churn_rate", precision = 5, scale = 4)
    private BigDecimal churnRate;

    // ==================== AUDIT FIELDS ====================

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    // ==================== ENUMS ====================

    public enum PeriodType {
        HOURLY,
        DAILY,
        WEEKLY,
        MONTHLY,
        QUARTERLY,
        YEARLY
    }

    // ==================== BUSINESS METHODS ====================

    /**
     * Calculates success rate from transaction counts
     */
    public void calculateSuccessRate() {
        if (transactionCount != null && transactionCount > 0) {
            BigDecimal rate = BigDecimal.valueOf(successfulTransactions)
                .divide(BigDecimal.valueOf(transactionCount), 4, java.math.RoundingMode.HALF_UP);
            this.successRate = rate;
        }
    }

    /**
     * Calculates average transaction amount
     */
    public void calculateAverageAmount() {
        if (transactionCount != null && transactionCount > 0 && totalAmount != null) {
            this.averageAmount = totalAmount.divide(
                BigDecimal.valueOf(transactionCount), 4, java.math.RoundingMode.HALF_UP);
        }
    }

    /**
     * Validates that counts add up correctly
     */
    public boolean validateCountIntegrity() {
        if (transactionCount == null) return false;
        long calculatedTotal = (successfulTransactions != null ? successfulTransactions : 0)
            + (failedTransactions != null ? failedTransactions : 0)
            + (pendingTransactions != null ? pendingTransactions : 0);
        return calculatedTotal == transactionCount;
    }

    /**
     * Pre-persist hook to calculate derived metrics
     */
    @PrePersist
    @PreUpdate
    protected void onSave() {
        calculateSuccessRate();
        calculateAverageAmount();
    }
}
