package com.waqiti.frauddetection.entity;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Transaction Velocity Tracking Entity
 * 
 * Production-grade velocity tracking with:
 * - Multi-dimensional velocity checks
 * - Time-window based aggregation
 * - Real-time velocity calculation
 * - Threshold violation detection
 * 
 * @author Waqiti Fraud Detection Team
 */
@Entity
@Table(name = "transaction_velocity", indexes = {
    @Index(name = "idx_velocity_user", columnList = "user_id"),
    @Index(name = "idx_velocity_account", columnList = "account_id"),
    @Index(name = "idx_velocity_type", columnList = "velocity_type"),
    @Index(name = "idx_velocity_window", columnList = "window_start, window_end"),
    @Index(name = "idx_velocity_created", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionVelocity {
    
    @Id
    @Column(name = "id", length = 36)
    private String id;
    
    // Entity Tracking
    @Column(name = "user_id", length = 36)
    private String userId;
    
    @Column(name = "account_id", length = 36)
    private String accountId;
    
    @Column(name = "card_id", length = 36)
    private String cardId;
    
    @Column(name = "device_id", length = 64)
    private String deviceId;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress; // IPv4 or IPv6
    
    @Column(name =="merchant_id", length = 36)
    private String merchantId;
    
    // Velocity Type
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "velocity_type", nullable = false, length = 50)
    private VelocityType velocityType;
    
    // Time Window
    @NotNull
    @Column(name = "window_start", nullable = false)
    private LocalDateTime windowStart;
    
    @NotNull
    @Column(name = "window_end", nullable = false)
    private LocalDateTime windowEnd;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "window_duration", nullable = false, length = 20)
    private WindowDuration windowDuration;
    
    // Velocity Metrics
    @Column(name = "transaction_count", nullable = false)
    @Builder.Default
    private Integer transactionCount = 0;
    
    @Column(name = "total_amount", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;
    
    @Column(name = "avg_amount", precision = 19, scale = 4)
    private BigDecimal avgAmount;
    
    @Column(name = "max_amount", precision = 19, scale = 4)
    private BigDecimal maxAmount;
    
    @Column(name = "min_amount", precision = 19, scale = 4)
    private BigDecimal minAmount;
    
    @Column(name = "currency", length = 3)
    private String currency;
    
    // Unique Counts
    @Column(name = "unique_merchants")
    private Integer uniqueMerchants;
    
    @Column(name = "unique_devices")
    private Integer uniqueDevices;
    
    @Column(name = "unique_ip_addresses")
    private Integer uniqueIpAddresses;
    
    @Column(name = "unique_countries")
    private Integer uniqueCountries;
    
    // Threshold Tracking
    @Column(name = "threshold_count")
    private Integer thresholdCount;
    
    @Column(name = "threshold_amount", precision = 19, scale = 4)
    private BigDecimal thresholdAmount;
    
    @Column(name = "threshold_exceeded", nullable = false)
    @Builder.Default
    private Boolean thresholdExceeded = false;
    
    @Column(name = "threshold_exceeded_at")
    private LocalDateTime thresholdExceededAt;
    
    @Column(name = "violation_count")
    @Builder.Default
    private Integer violationCount = 0;
    
    // Risk Indicators
    @Column(name = "rapid_succession", nullable = false)
    @Builder.Default
    private Boolean rapidSuccession = false; // Transactions within seconds
    
    @Column(name = "unusual_pattern", nullable = false)
    @Builder.Default
    private Boolean unusualPattern = false; // Deviates from historical behavior
    
    @Column(name = "velocity_spike", nullable = false)
    @Builder.Default
    private Boolean velocitySpike = false; // Sudden increase in velocity
    
    @Column(name = "risk_score", precision = 5, scale = 4)
    private BigDecimal riskScore; // 0.0000 to 1.0000
    
    // Historical Comparison
    @Column(name = "historical_avg_count")
    private Integer historicalAvgCount;
    
    @Column(name = "historical_avg_amount", precision = 19, scale = 4)
    private BigDecimal historicalAvgAmount;
    
    @Column(name = "deviation_percentage", precision = 7, scale = 4)
    private BigDecimal deviationPercentage;
    
    // Status
    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;
    
    @Column(name = "expired", nullable = false)
    @Builder.Default
    private Boolean expired = false;
    
    // Audit Fields
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "last_transaction_at")
    private LocalDateTime lastTransactionAt;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    /**
     * Check if window is currently active
     */
    public boolean isWindowActive() {
        LocalDateTime now = LocalDateTime.now();
        return active && !expired && 
               !now.isBefore(windowStart) && !now.isAfter(windowEnd);
    }
    
    /**
     * Calculate velocity rate (transactions per hour)
     */
    public Double getVelocityRate() {
        long hours = java.time.Duration.between(windowStart, windowEnd).toHours();
        return hours > 0 ? (double) transactionCount / hours : transactionCount.doubleValue();
    }
    
    /**
     * Check if significantly deviates from historical
     */
    public boolean hasSignificantDeviation() {
        return deviationPercentage != null && 
               deviationPercentage.compareTo(new BigDecimal("50.00")) > 0;
    }
    
    /**
     * Velocity Type Enum
     */
    public enum VelocityType {
        USER_TRANSACTION_COUNT,
        USER_TRANSACTION_AMOUNT,
        ACCOUNT_TRANSACTION_COUNT,
        ACCOUNT_TRANSACTION_AMOUNT,
        CARD_TRANSACTION_COUNT,
        CARD_TRANSACTION_AMOUNT,
        DEVICE_TRANSACTION_COUNT,
        IP_TRANSACTION_COUNT,
        MERCHANT_PER_USER,
        COUNTRY_PER_USER,
        FAILED_ATTEMPTS,
        CROSS_BORDER_TRANSACTIONS
    }
    
    /**
     * Window Duration Enum
     */
    public enum WindowDuration {
        MINUTE_1,
        MINUTE_5,
        MINUTE_15,
        MINUTE_30,
        HOUR_1,
        HOUR_6,
        HOUR_12,
        DAY_1,
        DAY_7,
        DAY_30
    }
}
