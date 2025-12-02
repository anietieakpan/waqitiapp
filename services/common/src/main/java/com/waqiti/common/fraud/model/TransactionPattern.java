package com.waqiti.common.fraud.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Transaction pattern model for fraud detection
 * Analyzes and stores behavioral patterns to detect anomalies
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "transaction_patterns", indexes = {
    @Index(name = "idx_pattern_user_id", columnList = "user_id"),
    @Index(name = "idx_pattern_type", columnList = "pattern_type"),
    @Index(name = "idx_pattern_status", columnList = "status"),
    @Index(name = "idx_pattern_updated", columnList = "last_updated")
})
public class TransactionPattern {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "pattern_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private PatternType patternType;

    // Individual transaction fields for pattern building
    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    @Column(name = "location")
    private String location;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "pattern_name")
    private String patternName;

    @Column(name = "pattern_description", columnDefinition = "TEXT")
    private String patternDescription;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PatternStatus status = PatternStatus.ACTIVE;

    @Column(name = "confidence_score", precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(name = "frequency_score", precision = 5, scale = 2)
    private BigDecimal frequencyScore;

    @Column(name = "stability_score", precision = 5, scale = 2)
    private BigDecimal stabilityScore;

    @Column(name = "anomaly_threshold", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal anomalyThreshold = new BigDecimal("75.00");

    // Temporal patterns
    @ElementCollection
    @CollectionTable(name = "pattern_preferred_hours", 
        joinColumns = @JoinColumn(name = "pattern_id"))
    @Column(name = "hour_of_day")
    private List<Integer> preferredHours;

    @ElementCollection
    @CollectionTable(name = "pattern_preferred_days", 
        joinColumns = @JoinColumn(name = "pattern_id"))
    @Column(name = "day_of_week")
    private List<Integer> preferredDaysOfWeek;

    @Column(name = "typical_start_time")
    private LocalTime typicalStartTime;

    @Column(name = "typical_end_time")
    private LocalTime typicalEndTime;

    // Amount patterns
    @Column(name = "average_amount", precision = 19, scale = 2)
    private BigDecimal averageAmount;

    @Column(name = "median_amount", precision = 19, scale = 2)
    private BigDecimal medianAmount;

    @Column(name = "min_amount", precision = 19, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 19, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "standard_deviation", precision = 19, scale = 2)
    private BigDecimal standardDeviation;

    @ElementCollection
    @CollectionTable(name = "pattern_amount_ranges", 
        joinColumns = @JoinColumn(name = "pattern_id"))
    @MapKeyColumn(name = "range_name")
    @Column(name = "frequency_percentage")
    private Map<String, BigDecimal> amountRangeFrequencies;

    // Geographic patterns
    @ElementCollection
    @CollectionTable(name = "pattern_preferred_countries", 
        joinColumns = @JoinColumn(name = "pattern_id"))
    @Column(name = "country_code")
    private List<String> preferredCountries;

    @ElementCollection
    @CollectionTable(name = "pattern_preferred_cities", 
        joinColumns = @JoinColumn(name = "pattern_id"))
    @Column(name = "city_name")
    private List<String> preferredCities;

    @Column(name = "home_country")
    private String homeCountry;

    @Column(name = "cross_border_frequency", precision = 5, scale = 2)
    private BigDecimal crossBorderFrequency;

    // Merchant patterns
    @ElementCollection
    @CollectionTable(name = "pattern_preferred_merchants", 
        joinColumns = @JoinColumn(name = "pattern_id"))
    @Column(name = "merchant_id")
    private List<String> preferredMerchants;

    @ElementCollection
    @CollectionTable(name = "pattern_merchant_categories", 
        joinColumns = @JoinColumn(name = "pattern_id"))
    @MapKeyColumn(name = "category_code")
    @Column(name = "usage_frequency")
    private Map<String, BigDecimal> merchantCategoryFrequencies;

    // Device patterns
    @ElementCollection
    @CollectionTable(name = "pattern_trusted_devices", 
        joinColumns = @JoinColumn(name = "pattern_id"))
    @Column(name = "device_fingerprint")
    private List<String> trustedDevices;

    @Column(name = "device_consistency_score", precision = 5, scale = 2)
    private BigDecimal deviceConsistencyScore;

    @Column(name = "new_device_frequency", precision = 5, scale = 2)
    private BigDecimal newDeviceFrequency;

    // Channel patterns
    @ElementCollection
    @CollectionTable(name = "pattern_preferred_channels", 
        joinColumns = @JoinColumn(name = "pattern_id"))
    @MapKeyColumn(name = "channel_type")
    @Column(name = "usage_percentage")
    private Map<String, BigDecimal> channelPreferences;

    // Velocity patterns
    @Column(name = "average_daily_transactions")
    private BigDecimal averageDailyTransactions;

    @Column(name = "average_daily_amount", precision = 19, scale = 2)
    private BigDecimal averageDailyAmount;

    @Column(name = "max_daily_transactions")
    private Integer maxDailyTransactions;

    @Column(name = "max_daily_amount", precision = 19, scale = 2)
    private BigDecimal maxDailyAmount;

    @Column(name = "burst_transaction_threshold")
    private Integer burstTransactionThreshold;

    @Column(name = "cooling_period_minutes")
    private Integer coolingPeriodMinutes;

    // Behavioral patterns
    @Column(name = "session_duration_average")
    private Long sessionDurationAverage; // in seconds

    @Column(name = "pages_per_session_average")
    private BigDecimal pagesPerSessionAverage;

    @Column(name = "form_completion_time_average")
    private Long formCompletionTimeAverage; // in seconds

    @Column(name = "typing_pattern_signature")
    private String typingPatternSignature;

    @Column(name = "navigation_pattern_signature")
    private String navigationPatternSignature;

    // Statistical data
    @Column(name = "sample_size")
    private Integer sampleSize;

    @Column(name = "observation_period_days")
    private Integer observationPeriodDays;

    @Column(name = "created_at", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @Column(name = "last_updated", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastUpdated;

    @Column(name = "last_validated")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastValidated;

    @Column(name = "validation_score", precision = 5, scale = 2)
    private BigDecimal validationScore;

    @Column(name = "model_version")
    private String modelVersion;

    @Column(name = "learning_mode", nullable = false)
    @Builder.Default
    private Boolean learningMode = true;

    @Column(name = "auto_update_enabled", nullable = false)
    @Builder.Default
    private Boolean autoUpdateEnabled = true;

    @Version
    private Long version;

    /**
     * Pattern types
     */
    public enum PatternType {
        TEMPORAL,           // Time-based patterns
        AMOUNT,             // Transaction amount patterns
        GEOGRAPHIC,         // Location-based patterns
        MERCHANT,           // Merchant/category patterns
        DEVICE,             // Device usage patterns
        CHANNEL,            // Channel preference patterns
        VELOCITY,           // Transaction frequency patterns
        BEHAVIORAL,         // User behavior patterns
        COMBINED,           // Multi-dimensional patterns
        SEASONAL,           // Seasonal/cyclical patterns
        PEER_GROUP,         // Peer comparison patterns
        LIFECYCLE,          // Account lifecycle patterns
        RISK_BASED          // Risk-adjusted patterns
    }

    /**
     * Pattern status
     */
    public enum PatternStatus {
        LEARNING,           // Still building pattern
        ACTIVE,             // Active monitoring
        STABLE,             // Established and stable
        EVOLVING,           // Pattern is changing
        DEPRECATED,         // No longer relevant
        ANOMALOUS,          // Pattern shows anomalies
        SUSPENDED           // Temporarily disabled
    }

    /**
     * Pre-persist callback
     */
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        lastUpdated = LocalDateTime.now();
    }

    /**
     * Pre-update callback
     */
    @PreUpdate
    public void preUpdate() {
        lastUpdated = LocalDateTime.now();
    }

    /**
     * Check if transaction deviates from this pattern
     */
    public boolean isAnomaly(TransactionEvent transaction) {
        BigDecimal deviationScore = calculateDeviationScore(transaction);
        return deviationScore.compareTo(anomalyThreshold) > 0;
    }

    /**
     * Calculate how much a transaction deviates from this pattern
     */
    public BigDecimal calculateDeviationScore(TransactionEvent transaction) {
        BigDecimal totalDeviation = BigDecimal.ZERO;
        int factors = 0;

        // Check amount deviation
        if (averageAmount != null && transaction.getAmount() != null) {
            BigDecimal amountDeviation = calculateAmountDeviation(transaction.getAmount());
            totalDeviation = totalDeviation.add(amountDeviation);
            factors++;
        }

        // Check time deviation
        BigDecimal timeDeviation = calculateTimeDeviation(transaction.getTimestamp());
        totalDeviation = totalDeviation.add(timeDeviation);
        factors++;

        // Check geographic deviation
        if (transaction.getLocationInfo() != null) {
            BigDecimal geoDeviation = calculateGeographicDeviation(transaction.getLocationInfo());
            totalDeviation = totalDeviation.add(geoDeviation);
            factors++;
        }

        // Check merchant deviation
        if (transaction.getMerchantId() != null) {
            BigDecimal merchantDeviation = calculateMerchantDeviation(transaction.getMerchantId());
            totalDeviation = totalDeviation.add(merchantDeviation);
            factors++;
        }

        // Check device deviation
        if (transaction.getDeviceInfo() != null) {
            BigDecimal deviceDeviation = calculateDeviceDeviation(transaction.getDeviceInfo());
            totalDeviation = totalDeviation.add(deviceDeviation);
            factors++;
        }

        return factors > 0 ? totalDeviation.divide(new BigDecimal(factors), 2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    /**
     * Calculate amount deviation from pattern
     */
    private BigDecimal calculateAmountDeviation(BigDecimal amount) {
        if (averageAmount == null || standardDeviation == null) {
            return new BigDecimal("50"); // Default moderate deviation
        }

        BigDecimal zScore = amount.subtract(averageAmount)
                                 .abs()
                                 .divide(standardDeviation, 4, java.math.RoundingMode.HALF_UP);

        // Convert Z-score to percentage (capped at 100)
        return zScore.multiply(new BigDecimal("20")).min(new BigDecimal("100"));
    }

    /**
     * Calculate time deviation from pattern
     */
    private BigDecimal calculateTimeDeviation(LocalDateTime timestamp) {
        if (timestamp == null) {
            return new BigDecimal("50");
        }

        int hour = timestamp.getHour();
        int dayOfWeek = timestamp.getDayOfWeek().getValue();

        BigDecimal hourDeviation = preferredHours != null && !preferredHours.contains(hour) ? 
                                  new BigDecimal("30") : BigDecimal.ZERO;

        BigDecimal dayDeviation = preferredDaysOfWeek != null && !preferredDaysOfWeek.contains(dayOfWeek) ? 
                                 new BigDecimal("20") : BigDecimal.ZERO;

        return hourDeviation.add(dayDeviation);
    }

    /**
     * Calculate geographic deviation from pattern
     */
    private BigDecimal calculateGeographicDeviation(TransactionEvent.LocationInfo location) {
        if (location == null) {
            return new BigDecimal("50");
        }

        BigDecimal deviation = BigDecimal.ZERO;

        if (preferredCountries != null && !preferredCountries.contains(location.getCountryCode())) {
            deviation = deviation.add(new BigDecimal("40"));
        }

        if (preferredCities != null && !preferredCities.contains(location.getCity())) {
            deviation = deviation.add(new BigDecimal("20"));
        }

        return deviation;
    }

    /**
     * Calculate merchant deviation from pattern
     */
    private BigDecimal calculateMerchantDeviation(String merchantId) {
        if (merchantId == null) {
            return new BigDecimal("50");
        }

        if (preferredMerchants != null && preferredMerchants.contains(merchantId)) {
            return BigDecimal.ZERO; // Known merchant
        }

        return new BigDecimal("30"); // Unknown merchant
    }

    /**
     * Calculate device deviation from pattern
     */
    private BigDecimal calculateDeviceDeviation(TransactionEvent.DeviceInfo deviceInfo) {
        if (deviceInfo == null) {
            return new BigDecimal("50");
        }

        if (trustedDevices != null && trustedDevices.contains(deviceInfo.getDeviceFingerprint())) {
            return BigDecimal.ZERO; // Trusted device
        }

        return new BigDecimal("35"); // Unknown device
    }

    /**
     * Update pattern with new transaction data
     */
    public void updateWithTransaction(TransactionEvent transaction) {
        if (!autoUpdateEnabled || status == PatternStatus.SUSPENDED) {
            return;
        }

        // Update sample size
        sampleSize = (sampleSize == null ? 0 : sampleSize) + 1;

        // Update amount statistics
        updateAmountStatistics(transaction.getAmount());

        // Update temporal patterns
        updateTemporalPatterns(transaction.getTimestamp());

        // Update geographic patterns
        if (transaction.getLocationInfo() != null) {
            updateGeographicPatterns(transaction.getLocationInfo());
        }

        // Update merchant patterns
        if (transaction.getMerchantId() != null) {
            updateMerchantPatterns(transaction.getMerchantId(), transaction.getMerchantCategory());
        }

        // Update device patterns
        if (transaction.getDeviceInfo() != null) {
            updateDevicePatterns(transaction.getDeviceInfo());
        }

        // Recalculate confidence scores
        recalculateConfidenceScores();

        lastUpdated = LocalDateTime.now();
    }

    /**
     * Update amount-related statistics
     */
    private void updateAmountStatistics(BigDecimal amount) {
        if (amount == null) return;

        if (averageAmount == null) {
            averageAmount = amount;
            minAmount = amount;
            maxAmount = amount;
        } else {
            // Update running average
            BigDecimal n = new BigDecimal(sampleSize);
            averageAmount = averageAmount.multiply(n.subtract(BigDecimal.ONE))
                                        .add(amount)
                                        .divide(n, 2, java.math.RoundingMode.HALF_UP);

            if (amount.compareTo(minAmount) < 0) minAmount = amount;
            if (amount.compareTo(maxAmount) > 0) maxAmount = amount;
        }
    }

    /**
     * Update temporal patterns
     */
    private void updateTemporalPatterns(LocalDateTime timestamp) {
        if (timestamp == null) return;

        int hour = timestamp.getHour();
        int dayOfWeek = timestamp.getDayOfWeek().getValue();

        if (preferredHours == null) {
            preferredHours = new java.util.ArrayList<>();
        }
        if (!preferredHours.contains(hour)) {
            preferredHours.add(hour);
        }

        if (preferredDaysOfWeek == null) {
            preferredDaysOfWeek = new java.util.ArrayList<>();
        }
        if (!preferredDaysOfWeek.contains(dayOfWeek)) {
            preferredDaysOfWeek.add(dayOfWeek);
        }
    }

    /**
     * Update geographic patterns
     */
    private void updateGeographicPatterns(TransactionEvent.LocationInfo location) {
        if (preferredCountries == null) {
            preferredCountries = new java.util.ArrayList<>();
        }
        if (location.getCountryCode() != null && !preferredCountries.contains(location.getCountryCode())) {
            preferredCountries.add(location.getCountryCode());
        }

        if (preferredCities == null) {
            preferredCities = new java.util.ArrayList<>();
        }
        if (location.getCity() != null && !preferredCities.contains(location.getCity())) {
            preferredCities.add(location.getCity());
        }
    }

    /**
     * Update merchant patterns
     */
    private void updateMerchantPatterns(String merchantId, String merchantCategory) {
        if (preferredMerchants == null) {
            preferredMerchants = new java.util.ArrayList<>();
        }
        if (!preferredMerchants.contains(merchantId)) {
            preferredMerchants.add(merchantId);
        }

        if (merchantCategory != null) {
            if (merchantCategoryFrequencies == null) {
                merchantCategoryFrequencies = new java.util.HashMap<>();
            }
            merchantCategoryFrequencies.merge(merchantCategory, BigDecimal.ONE, BigDecimal::add);
        }
    }

    /**
     * Update device patterns
     */
    private void updateDevicePatterns(TransactionEvent.DeviceInfo deviceInfo) {
        if (trustedDevices == null) {
            trustedDevices = new java.util.ArrayList<>();
        }
        
        String fingerprint = deviceInfo.getDeviceFingerprint();
        if (fingerprint != null && !trustedDevices.contains(fingerprint)) {
            trustedDevices.add(fingerprint);
        }
    }

    /**
     * Recalculate confidence scores
     */
    private void recalculateConfidenceScores() {
        if (sampleSize == null || sampleSize < 5) {
            confidenceScore = new BigDecimal("0.1"); // Low confidence
            return;
        }

        // Base confidence on sample size
        BigDecimal sizeScore = new BigDecimal(Math.min(sampleSize, 100)).divide(new BigDecimal("100"), 4, java.math.RoundingMode.HALF_UP);
        
        // Adjust for observation period
        BigDecimal timeScore = observationPeriodDays != null && observationPeriodDays >= 30 ? 
                              new BigDecimal("1.0") : new BigDecimal("0.7");

        confidenceScore = sizeScore.multiply(timeScore).min(new BigDecimal("1.0"));
    }

    /**
     * Check if pattern is mature enough for anomaly detection
     */
    public boolean isMatureForDetection() {
        return confidenceScore != null && 
               confidenceScore.compareTo(new BigDecimal("0.7")) >= 0 &&
               sampleSize != null && 
               sampleSize >= 10 &&
               status == PatternStatus.ACTIVE;
    }

    /**
     * Get pattern strength indicator
     */
    public String getStrengthIndicator() {
        if (confidenceScore == null) return "UNKNOWN";
        
        if (confidenceScore.compareTo(new BigDecimal("0.9")) >= 0) return "VERY_STRONG";
        if (confidenceScore.compareTo(new BigDecimal("0.7")) >= 0) return "STRONG";
        if (confidenceScore.compareTo(new BigDecimal("0.5")) >= 0) return "MODERATE";
        if (confidenceScore.compareTo(new BigDecimal("0.3")) >= 0) return "WEAK";
        return "VERY_WEAK";
    }
}