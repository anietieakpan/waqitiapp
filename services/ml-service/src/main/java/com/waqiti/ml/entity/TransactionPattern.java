package com.waqiti.ml.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;

/**
 * Production-ready Transaction Pattern entity for ML analysis.
 * Stores detailed transaction patterns and behavioral data.
 */
@Entity
@Table(name = "transaction_patterns",
       indexes = {
           @Index(name = "idx_transaction_pattern_user_id", columnList = "user_id"),
           @Index(name = "idx_transaction_pattern_timestamp", columnList = "timestamp DESC"),
           @Index(name = "idx_transaction_pattern_user_timestamp", columnList = "user_id, timestamp DESC"),
           @Index(name = "idx_transaction_pattern_target_account", columnList = "target_account"),
           @Index(name = "idx_transaction_pattern_device_id", columnList = "device_id"),
           @Index(name = "idx_transaction_pattern_amount", columnList = "amount DESC"),
           @Index(name = "idx_transaction_pattern_hour_dow", columnList = "hour_of_day, day_of_week")
       })
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"extendedMetadata", "riskFactors"})
public class TransactionPattern {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    @EqualsAndHashCode.Include
    private String userId;

    @Column(name = "transaction_id", nullable = false, unique = true, length = 36)
    @EqualsAndHashCode.Include
    private String transactionId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @CreationTimestamp
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "hour_of_day", nullable = false)
    private Integer hourOfDay;

    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "day_of_month")
    private Integer dayOfMonth;

    @Column(name = "month_of_year")
    private Integer monthOfYear;

    @Column(name = "transaction_type", length = 50)
    private String transactionType;

    @Column(name = "source_account", length = 100)
    private String sourceAccount;

    @Column(name = "target_account", length = 100)
    private String targetAccount;

    @Column(name = "merchant_id", length = 36)
    private String merchantId;

    @Column(name = "merchant_category", length = 50)
    private String merchantCategory;

    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Column(name = "device_type", length = 50)
    private String deviceType; // MOBILE, DESKTOP, TABLET

    @Column(name = "ip_address", length = 45) // IPv6 support
    private String ipAddress;

    @Column(name = "location", length = 200)
    private String location;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "latitude", precision = 10, scale = 7)
    private Double latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private Double longitude;

    @Column(name = "channel", length = 20)
    private String channel; // MOBILE, WEB, API, ATM

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "authentication_method", length = 50)
    private String authenticationMethod;

    @Column(name = "session_duration_minutes")
    private Integer sessionDurationMinutes;

    @Column(name = "is_weekend")
    private Boolean isWeekend;

    @Column(name = "is_night_time")
    private Boolean isNightTime;

    @Column(name = "is_international")
    private Boolean isInternational;

    @Column(name = "is_high_value")
    private Boolean isHighValue;

    @Column(name = "is_round_amount")
    private Boolean isRoundAmount;

    @Column(name = "is_recurring")
    private Boolean isRecurring;

    @Column(name = "risk_score", precision = 5, scale = 2)
    private Double riskScore;

    @Column(name = "fraud_probability", precision = 5, scale = 4)
    private Double fraudProbability;

    @Column(name = "anomaly_score", precision = 5, scale = 4)
    private Double anomalyScore;

    @Column(name = "velocity_score", precision = 5, scale = 2)
    private Double velocityScore;

    @Column(name = "behavioral_score", precision = 5, scale = 2)
    private Double behavioralScore;

    @Column(name = "network_risk_score", precision = 5, scale = 2)
    private Double networkRiskScore;

    @Column(name = "device_trust_score", precision = 5, scale = 4)
    private Double deviceTrustScore;

    @Column(name = "geolocation_risk_score", precision = 5, scale = 2)
    private Double geolocationRiskScore;

    // Compliance and regulatory fields
    @Column(name = "aml_flag")
    private Boolean amlFlag = false;

    @Column(name = "kyc_required")
    private Boolean kycRequired = false;

    @Column(name = "pep_screening")
    private Boolean pepScreening = false;

    @Column(name = "sanctions_screening")
    private Boolean sanctionsScreening = false;

    @Column(name = "enhanced_due_diligence")
    private Boolean enhancedDueDiligence = false;

    // JSON columns for complex metadata
    @Type(com.vladmihalcea.hibernate.type.json.JsonType.class)
    @Column(name = "extended_metadata", columnDefinition = "jsonb")
    private Map<String, Object> extendedMetadata = new HashMap<>();

    @Type(com.vladmihalcea.hibernate.type.json.JsonType.class)
    @Column(name = "risk_factors", columnDefinition = "jsonb")
    private Map<String, Double> riskFactors = new HashMap<>();

    @Type(com.vladmihalcea.hibernate.type.json.JsonType.class)
    @Column(name = "ml_features", columnDefinition = "jsonb")
    private Map<String, Double> mlFeatures = new HashMap<>();

    @Type(com.vladmihalcea.hibernate.type.json.JsonType.class)
    @Column(name = "behavioral_indicators", columnDefinition = "jsonb")
    private Map<String, Object> behavioralIndicators = new HashMap<>();

    // Audit and tracking
    @Column(name = "processed_by_ml")
    private Boolean processedByMl = false;

    @Column(name = "ml_model_version", length = 50)
    private String mlModelVersion;

    @Column(name = "analysis_timestamp")
    private LocalDateTime analysisTimestamp;

    @Column(name = "flagged_for_review")
    private Boolean flaggedForReview = false;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    /**
     * Initialize derived fields from timestamp
     */
    @PrePersist
    protected void initializeDerivedFields() {
        if (timestamp != null) {
            this.hourOfDay = timestamp.getHour();
            this.dayOfWeek = timestamp.getDayOfWeek().getValue();
            this.dayOfMonth = timestamp.getDayOfMonth();
            this.monthOfYear = timestamp.getMonthValue();
            this.isWeekend = (dayOfWeek == 6 || dayOfWeek == 7);
            this.isNightTime = (hourOfDay >= 22 || hourOfDay <= 6);
        }
        
        if (amount != null) {
            this.isHighValue = amount.compareTo(BigDecimal.valueOf(10000)) > 0;
            this.isRoundAmount = isRoundNumber(amount);
        }
        
        if (transactionType != null) {
            this.isInternational = "INTERNATIONAL".equals(transactionType);
        }
        
        if (analysisTimestamp == null) {
            this.analysisTimestamp = LocalDateTime.now();
        }
    }

    /**
     * Check if amount is a round number (potential indicator)
     */
    private boolean isRoundNumber(BigDecimal amount) {
        return amount.remainder(BigDecimal.valueOf(100)).compareTo(BigDecimal.ZERO) == 0 ||
               amount.remainder(BigDecimal.valueOf(50)).compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Add ML feature
     */
    public void addMLFeature(String featureName, Double featureValue) {
        if (mlFeatures == null) {
            mlFeatures = new HashMap<>();
        }
        mlFeatures.put(featureName, featureValue);
    }

    /**
     * Add risk factor
     */
    public void addRiskFactor(String factorName, Double factorValue) {
        if (riskFactors == null) {
            riskFactors = new HashMap<>();
        }
        riskFactors.put(factorName, factorValue);
    }

    /**
     * Add behavioral indicator
     */
    public void addBehavioralIndicator(String indicatorName, Object indicatorValue) {
        if (behavioralIndicators == null) {
            behavioralIndicators = new HashMap<>();
        }
        behavioralIndicators.put(indicatorName, indicatorValue);
    }

    /**
     * Flag transaction for manual review
     */
    public void flagForReview(String reason) {
        this.flaggedForReview = true;
        this.reviewNotes = reason;
    }

    /**
     * Check if transaction exhibits suspicious patterns
     */
    public boolean isSuspicious() {
        return Boolean.TRUE.equals(flaggedForReview) ||
               (riskScore != null && riskScore >= 70.0) ||
               Boolean.TRUE.equals(amlFlag);
    }

    /**
     * Get primary risk factor
     */
    public String getPrimaryRiskFactor() {
        if (riskFactors == null || riskFactors.isEmpty()) {
            return "UNKNOWN";
        }
        
        return riskFactors.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("UNKNOWN");
    }

    /**
     * Calculate time since analysis
     */
    public long getMinutesSinceAnalysis() {
        if (analysisTimestamp == null) return 0;
        return java.time.temporal.ChronoUnit.MINUTES.between(analysisTimestamp, LocalDateTime.now());
    }

    /**
     * Check if analysis is stale (needs refresh)
     */
    public boolean isAnalysisStale() {
        return getMinutesSinceAnalysis() > 60; // 1 hour threshold
    }
}