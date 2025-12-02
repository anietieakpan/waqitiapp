package com.waqiti.frauddetection.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Device Profile Entity
 *
 * Tracks device fingerprints, behavioral patterns, risk metrics, and fraud indicators
 * for device-based fraud detection. Supports device farm detection, impossible travel
 * detection, and multi-user device sharing analysis.
 *
 * PRODUCTION-GRADE ENTITY
 * - Optimistic locking with @Version for concurrent update protection
 * - Audit fields with JPA Auditing
 * - Strategic indexing for query performance
 * - Device fingerprint uniqueness constraint
 * - Collection tracking for associated users
 * - Geographic location tracking
 * - Impossible travel detection fields
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0 - Production Implementation
 */
@Entity
@Table(name = "device_profiles", indexes = {
    @Index(name = "idx_device_fingerprint", columnList = "device_fingerprint", unique = true),
    @Index(name = "idx_device_risk_level", columnList = "current_risk_level"),
    @Index(name = "idx_device_last_seen", columnList = "last_seen"),
    @Index(name = "idx_device_fraud_rate", columnList = "fraud_rate")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Optimistic locking version for concurrent update protection
     */
    @Version
    private Long version;

    /**
     * Device fingerprint (unique identifier)
     */
    @Column(name = "device_fingerprint", unique = true, nullable = false, length = 255)
    private String deviceFingerprint;

    /**
     * Transaction Statistics
     */
    @Column(name = "total_transactions", nullable = false)
    @Builder.Default
    private Long totalTransactions = 0L;

    @Column(name = "first_seen", nullable = false)
    private LocalDateTime firstSeen;

    @Column(name = "last_seen")
    private LocalDateTime lastSeen;

    /**
     * Associated Users (for device sharing and device farm detection)
     * Using ElementCollection to store user IDs in separate table
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "device_associated_users",
        joinColumns = @JoinColumn(name = "device_profile_id"),
        indexes = @Index(name = "idx_device_user_mapping", columnList = "device_profile_id")
    )
    @Column(name = "user_id")
    @Builder.Default
    private List<UUID> associatedUserIds = new ArrayList<>();

    /**
     * Risk Metrics
     */
    @Column(name = "average_risk_score", nullable = false)
    @Builder.Default
    private Double averageRiskScore = 0.0;

    @Column(name = "fraud_count", nullable = false)
    @Builder.Default
    private Integer fraudCount = 0;

    @Column(name = "fraud_rate", precision = 10, scale = 6, nullable = false)
    @Builder.Default
    private Double fraudRate = 0.0;

    @Column(name = "current_risk_level", nullable = false, length = 20)
    @Builder.Default
    private String currentRiskLevel = "UNKNOWN";

    /**
     * Location Tracking (for impossible travel detection)
     */
    @Column(name = "last_known_country", length = 3)
    private String lastKnownCountry;

    @Column(name = "last_known_latitude")
    private Double lastKnownLatitude;

    @Column(name = "last_known_longitude")
    private Double lastKnownLongitude;

    /**
     * Known countries (comma-separated list for geographic diversity analysis)
     */
    @Column(name = "known_countries", length = 500)
    private String knownCountries;

    /**
     * Impossible Travel Detection
     */
    @Column(name = "impossible_travel_detected", nullable = false)
    @Builder.Default
    private Boolean impossibleTravelDetected = false;

    @Column(name = "impossible_travel_count", nullable = false)
    @Builder.Default
    private Integer impossibleTravelCount = 0;

    /**
     * Fraud Timeline
     */
    @Column(name = "last_fraud_date")
    private LocalDateTime lastFraudDate;

    /**
     * Audit Fields
     */
    @CreatedDate
    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @LastModifiedDate
    @Column(name = "updated_date")
    private LocalDateTime updatedDate;

    /**
     * JPA Lifecycle Callbacks
     */
    @PrePersist
    protected void onCreate() {
        if (createdDate == null) {
            createdDate = LocalDateTime.now();
        }
        if (firstSeen == null) {
            firstSeen = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedDate = LocalDateTime.now();
    }

    /**
     * Helper method to add user to associated users list (with deduplication)
     */
    public void addAssociatedUser(UUID userId) {
        if (associatedUserIds == null) {
            associatedUserIds = new ArrayList<>();
        }
        if (!associatedUserIds.contains(userId)) {
            associatedUserIds.add(userId);
        }
    }

    /**
     * Helper method to get count of associated users
     */
    public int getAssociatedUserCount() {
        return associatedUserIds != null ? associatedUserIds.size() : 0;
    }

    /**
     * Helper method to check if device is new (less than 5 transactions)
     */
    public boolean isNewDevice() {
        return totalTransactions != null && totalTransactions < 5;
    }

    /**
     * Helper method to check if device has high fraud rate (>10%)
     */
    public boolean hasHighFraudRate() {
        return fraudRate != null && fraudRate > 0.1;
    }
}
