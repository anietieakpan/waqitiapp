package com.waqiti.analytics.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * User Analytics Entity
 *
 * Comprehensive user behavior and engagement analytics.
 * Tracks transaction patterns, engagement metrics, risk scores, and predictions.
 *
 * JSONB Fields (PostgreSQL-specific):
 * - features_used: Map of feature usage counts
 * - transaction_countries: Geographic distribution
 * - device_usage: Device type usage patterns
 *
 * @author Waqiti Platform Team
 * @version 1.0.0-PRODUCTION
 * @since 2025-11-10
 */
@Entity
@Table(name = "user_analytics", indexes = {
    @Index(name = "idx_user_analytics_user_date", columnList = "user_id, analysis_date"),
    @Index(name = "idx_user_analytics_date_period", columnList = "analysis_date, period_type"),
    @Index(name = "idx_user_analytics_risk_score", columnList = "risk_score"),
    @Index(name = "idx_user_analytics_churn", columnList = "churn_probability")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"featuresUsed", "transactionCountries", "deviceUsage"})
@EqualsAndHashCode(of = "id")
public class UserAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "analysis_date", nullable = false)
    private LocalDateTime analysisDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", length = 20, nullable = false)
    private PeriodType periodType;

    // ==================== TRANSACTION METRICS ====================

    @Column(name = "transaction_count", nullable = false)
    @Builder.Default
    private Long transactionCount = 0L;

    @Column(name = "successful_transactions", nullable = false)
    @Builder.Default
    private Long successfulTransactions = 0L;

    @Column(name = "failed_transactions", nullable = false)
    @Builder.Default
    private Long failedTransactions = 0L;

    @Column(name = "total_spent", precision = 19, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal totalSpent = BigDecimal.ZERO;

    @Column(name = "total_received", precision = 19, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal totalReceived = BigDecimal.ZERO;

    @Column(name = "net_flow", precision = 19, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal netFlow = BigDecimal.ZERO;

    @Column(name = "average_transaction_amount", precision = 19, scale = 4)
    private BigDecimal averageTransactionAmount;

    @Column(name = "largest_transaction", precision = 19, scale = 4)
    private BigDecimal largestTransaction;

    // ==================== BEHAVIORAL METRICS ====================

    @Column(name = "session_count")
    private Integer sessionCount;

    @Column(name = "total_session_duration_minutes")
    private Integer totalSessionDurationMinutes;

    @Column(name = "average_session_duration_minutes", precision = 8, scale = 2)
    private BigDecimal averageSessionDurationMinutes;

    @Column(name = "pages_viewed")
    private Integer pagesViewed;

    @Type(JsonBinaryType.class)
    @Column(name = "features_used", columnDefinition = "jsonb")
    private Map<String, Integer> featuresUsed;

    // ==================== ENGAGEMENT METRICS ====================

    @Column(name = "login_count")
    private Integer loginCount;

    @Column(name = "days_active")
    private Integer daysActive;

    @Column(name = "streak_days")
    private Integer streakDays;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "engagement_score", precision = 5, scale = 2)
    private BigDecimal engagementScore;

    // ==================== GEOGRAPHIC METRICS ====================

    @Column(name = "unique_countries")
    private Integer uniqueCountries;

    @Column(name = "primary_country", length = 3)
    private String primaryCountry;

    @Type(JsonBinaryType.class)
    @Column(name = "transaction_countries", columnDefinition = "jsonb")
    private Map<String, Integer> transactionCountries;

    // ==================== DEVICE METRICS ====================

    @Column(name = "unique_devices")
    private Integer uniqueDevices;

    @Column(name = "primary_device_type", length = 50)
    private String primaryDeviceType;

    @Type(JsonBinaryType.class)
    @Column(name = "device_usage", columnDefinition = "jsonb")
    private Map<String, Integer> deviceUsage;

    // ==================== RISK METRICS ====================

    @Column(name = "risk_score", precision = 5, scale = 2)
    private BigDecimal riskScore;

    @Column(name = "fraud_alerts")
    private Integer fraudAlerts;

    @Column(name = "compliance_issues")
    private Integer complianceIssues;

    @Column(name = "velocity_violations")
    private Integer velocityViolations;

    // ==================== FINANCIAL HEALTH ====================

    @Column(name = "account_balance", precision = 19, scale = 4)
    private BigDecimal accountBalance;

    @Column(name = "credit_utilization", precision = 5, scale = 4)
    private BigDecimal creditUtilization;

    @Column(name = "payment_reliability_score", precision = 5, scale = 2)
    private BigDecimal paymentReliabilityScore;

    // ==================== PREDICTIVE METRICS ====================

    @Column(name = "churn_probability", precision = 5, scale = 4)
    private BigDecimal churnProbability;

    @Column(name = "ltv_prediction", precision = 19, scale = 4)
    private BigDecimal ltvPrediction;

    @Column(name = "next_transaction_prediction")
    private LocalDateTime nextTransactionPrediction;

    // ==================== AUDIT FIELDS ====================

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    // ==================== ENUMS ====================

    public enum PeriodType {
        DAILY,
        WEEKLY,
        MONTHLY,
        QUARTERLY,
        YEARLY
    }

    // ==================== BUSINESS METHODS ====================

    /**
     * Calculate net flow from spent and received amounts
     */
    public void calculateNetFlow() {
        if (totalReceived != null && totalSpent != null) {
            this.netFlow = totalReceived.subtract(totalSpent);
        }
    }

    /**
     * Calculate average transaction amount
     */
    public void calculateAverageTransactionAmount() {
        if (transactionCount != null && transactionCount > 0 && totalSpent != null) {
            this.averageTransactionAmount = totalSpent.divide(
                BigDecimal.valueOf(transactionCount), 4, java.math.RoundingMode.HALF_UP);
        }
    }

    /**
     * Calculate engagement score (0-100)
     */
    public void calculateEngagementScore() {
        if (daysActive == null || loginCount == null) {
            this.engagementScore = BigDecimal.ZERO;
            return;
        }

        // Weighted score: 40% days active, 30% login frequency, 30% session duration
        BigDecimal daysScore = BigDecimal.valueOf(Math.min(daysActive, 30) * 100.0 / 30.0);
        BigDecimal loginScore = BigDecimal.valueOf(Math.min(loginCount, 60) * 100.0 / 60.0);
        BigDecimal sessionScore = BigDecimal.ZERO;

        if (averageSessionDurationMinutes != null) {
            sessionScore = BigDecimal.valueOf(
                Math.min(averageSessionDurationMinutes.doubleValue(), 30) * 100.0 / 30.0);
        }

        this.engagementScore = daysScore.multiply(BigDecimal.valueOf(0.4))
            .add(loginScore.multiply(BigDecimal.valueOf(0.3)))
            .add(sessionScore.multiply(BigDecimal.valueOf(0.3)))
            .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Determine if user is at risk of churning
     */
    public boolean isChurnRisk() {
        return churnProbability != null &&
               churnProbability.compareTo(BigDecimal.valueOf(0.7)) > 0;
    }

    /**
     * Determine if user is high risk
     */
    public boolean isHighRisk() {
        return riskScore != null &&
               riskScore.compareTo(BigDecimal.valueOf(75.0)) > 0;
    }

    @PrePersist
    @PreUpdate
    protected void onSave() {
        calculateNetFlow();
        calculateAverageTransactionAmount();
        calculateEngagementScore();
    }
}
