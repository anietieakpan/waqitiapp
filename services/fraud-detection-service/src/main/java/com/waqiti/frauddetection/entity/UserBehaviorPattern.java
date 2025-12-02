package com.waqiti.frauddetection.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing user behavior patterns for fraud detection
 */
@Entity
@Table(name = "user_behavior_patterns", indexes = {
    @Index(name = "idx_user_behavior_user_id", columnList = "user_id"),
    @Index(name = "idx_user_behavior_updated", columnList = "last_updated")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBehaviorPattern {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;
    
    // Timing patterns
    @Column(name = "typical_start_hour")
    private Integer typicalStartHour;
    
    @Column(name = "typical_end_hour")
    private Integer typicalEndHour;
    
    @Column(name = "weekend_active")
    @Builder.Default
    private boolean weekendActive = false;
    
    // Amount patterns
    @Column(name = "typical_min_amount", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal typicalMinAmount = BigDecimal.ZERO;
    
    @Column(name = "typical_max_amount", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal typicalMaxAmount = BigDecimal.valueOf(100);
    
    @Column(name = "typical_avg_amount", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal typicalAvgAmount = BigDecimal.valueOf(50);
    
    // Frequency patterns
    @Column(name = "typical_daily_transaction_count")
    @Builder.Default
    private int typicalDailyTransactionCount = 1;
    
    @Column(name = "typical_weekly_transaction_count")
    @Builder.Default
    private int typicalWeeklyTransactionCount = 5;
    
    @Column(name = "typical_monthly_transaction_count")
    @Builder.Default
    private int typicalMonthlyTransactionCount = 20;
    
    // Location patterns
    @Column(name = "typical_latitude")
    private Double typicalLatitude;
    
    @Column(name = "typical_longitude")
    private Double typicalLongitude;
    
    @Column(name = "typical_country")
    private String typicalCountry;
    
    @Column(name = "typical_city")
    private String typicalCity;
    
    @Column(name = "location_variance_km")
    @Builder.Default
    private double locationVarianceKm = 50.0;
    
    // Device patterns
    @Column(name = "common_device_fingerprint")
    private String commonDeviceFingerprint;
    
    @Column(name = "multi_device_user")
    @Builder.Default
    private boolean multiDeviceUser = false;
    
    @Column(name = "trusted_device_count")
    @Builder.Default
    private int trustedDeviceCount = 1;
    
    // Behavioral patterns
    @Column(name = "typical_session_duration_minutes")
    @Builder.Default
    private int typicalSessionDurationMinutes = 10;
    
    @Column(name = "multi_location_user")
    @Builder.Default
    private boolean multiLocationUser = false;
    
    @Column(name = "international_user")
    @Builder.Default
    private boolean internationalUser = false;
    
    // Risk indicators
    @Column(name = "historical_fraud_score", precision = 5, scale = 2)
    @Builder.Default
    private Double historicalFraudScore = 0.0;
    
    @Column(name = "false_positive_count")
    @Builder.Default
    private int falsePositiveCount = 0;
    
    @Column(name = "confirmed_fraud_count")
    @Builder.Default
    private int confirmedFraudCount = 0;
    
    // Learning data
    @Column(name = "pattern_confidence", precision = 5, scale = 2)
    @Builder.Default
    private Double patternConfidence = 50.0;
    
    @Column(name = "data_points_count")
    @Builder.Default
    private int dataPointsCount = 0;
    
    @Column(name = "learning_phase")
    @Builder.Default
    private boolean learningPhase = true;
    
    // Timestamps
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;
    
    @Column(name = "last_transaction_time")
    private LocalDateTime lastTransactionTime;
    
    // Version for optimistic locking
    @Version
    private Long version;
    
    /**
     * Update pattern confidence based on data points
     */
    public void updatePatternConfidence() {
        if (dataPointsCount < 10) {
            patternConfidence = 30.0;
            learningPhase = true;
        } else if (dataPointsCount < 50) {
            patternConfidence = 60.0;
            learningPhase = true;
        } else if (dataPointsCount < 100) {
            patternConfidence = 80.0;
            learningPhase = false;
        } else {
            patternConfidence = 95.0;
            learningPhase = false;
        }
    }
    
    /**
     * Calculate risk adjustment based on historical data
     */
    public double getRiskAdjustment() {
        if (confirmedFraudCount > 0) {
            return 1.5; // Increase risk for users with fraud history
        } else if (falsePositiveCount > 5) {
            return 0.7; // Decrease risk for users with many false positives
        }
        return 1.0; // Normal risk
    }
    
    /**
     * Check if pattern has enough data for reliable predictions
     */
    public boolean hasReliablePattern() {
        return dataPointsCount >= 20 && patternConfidence >= 70.0;
    }
    
    /**
     * Increment data points and update confidence
     */
    public void addDataPoint() {
        dataPointsCount++;
        updatePatternConfidence();
    }
    
    /**
     * Record false positive
     */
    public void recordFalsePositive() {
        falsePositiveCount++;
        // Adjust historical fraud score down
        if (historicalFraudScore > 10.0) {
            historicalFraudScore -= 5.0;
        }
    }
    
    /**
     * Record confirmed fraud
     */
    public void recordConfirmedFraud() {
        confirmedFraudCount++;
        // Increase historical fraud score
        historicalFraudScore += 20.0;
        if (historicalFraudScore > 100.0) {
            historicalFraudScore = 100.0;
        }
    }
}