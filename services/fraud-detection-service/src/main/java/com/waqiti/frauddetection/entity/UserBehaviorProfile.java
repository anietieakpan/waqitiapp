package com.waqiti.frauddetection.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * User Behavior Profile for Fraud False Positive Optimization
 *
 * Tracks user transaction patterns to reduce false positives
 * and improve fraud detection accuracy.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-02
 */
@Entity
@Table(name = "user_behavior_profiles", indexes = {
    @Index(name = "idx_user_behavior_user_id", columnList = "user_id", unique = true),
    @Index(name = "idx_user_behavior_last_transaction", columnList = "last_transaction_at")
})
@Data
public class UserBehaviorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    // Transaction statistics
    @Column(name = "successful_transaction_count", nullable = false)
    private Integer successfulTransactionCount = 0;

    @Column(name = "fraud_incident_count", nullable = false)
    private Integer fraudIncidentCount = 0;

    @Column(name = "false_positive_count", nullable = false)
    private Integer falsePositiveCount = 0;

    @Column(name = "average_transaction_amount", precision = 19, scale = 4)
    private BigDecimal averageTransactionAmount;

    @Column(name = "transaction_amount_std_dev", precision = 19, scale = 4)
    private BigDecimal transactionAmountStdDev;

    @Column(name = "consistency_score")
    private Double consistencyScore;

    // Verification status
    @Column(name = "kyc_verified", nullable = false)
    private boolean kycVerified = false;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified = false;

    // Behavioral patterns
    @ElementCollection
    @CollectionTable(name = "user_typical_hours", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "hour")
    private Set<Integer> typicalTransactionHours;

    @ElementCollection
    @CollectionTable(name = "user_trusted_devices", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "device_fingerprint")
    private Set<String> trustedDevices;

    @ElementCollection
    @CollectionTable(name = "user_blacklisted_devices", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "device_fingerprint")
    private Set<String> blacklistedDevices;

    @ElementCollection
    @CollectionTable(name = "user_trusted_ip_ranges", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "ip_range")
    private Set<String> trustedIpRanges;

    @ElementCollection
    @CollectionTable(name = "user_typical_countries", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "country_code")
    private Set<String> typicalCountries;

    @ElementCollection
    @CollectionTable(name = "user_typical_merchant_categories", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "category")
    private Set<String> typicalMerchantCategories;

    // Timestamps
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_transaction_at")
    private LocalDateTime lastTransactionAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
