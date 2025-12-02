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
 * Location Profile Entity
 *
 * Tracks IP addresses, geographic locations, VPN/proxy usage, and risk metrics
 * for location-based fraud detection. Supports high-risk jurisdiction detection,
 * geographic anomaly detection, and IP reputation tracking.
 *
 * PRODUCTION-GRADE ENTITY
 * - Optimistic locking with @Version for concurrent update protection
 * - Audit fields with JPA Auditing
 * - Strategic indexing for query performance
 * - IP address uniqueness constraint
 * - Geographic coordinate storage
 * - VPN/Proxy detection flags
 * - ISP and ASN tracking
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0 - Production Implementation
 */
@Entity
@Table(name = "location_profiles", indexes = {
    @Index(name = "idx_location_ip_address", columnList = "ip_address", unique = true),
    @Index(name = "idx_location_country_code", columnList = "country_code"),
    @Index(name = "idx_location_risk_level", columnList = "current_risk_level"),
    @Index(name = "idx_location_is_vpn", columnList = "is_vpn_or_proxy"),
    @Index(name = "idx_location_last_seen", columnList = "last_seen_date")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Optimistic locking version for concurrent update protection
     */
    @Version
    private Long version;

    /**
     * IP Address (unique identifier)
     */
    @Column(name = "ip_address", unique = true, nullable = false, length = 45)
    private String ipAddress; // Support both IPv4 and IPv6

    /**
     * Geographic Information
     */
    @Column(name = "country_code", length = 3)
    private String countryCode; // ISO 3166-1 alpha-2

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    /**
     * Network Information
     */
    @Column(name = "isp", length = 255)
    private String isp; // Internet Service Provider

    @Column(name = "asn", length = 20)
    private String asn; // Autonomous System Number

    /**
     * VPN/Proxy Detection
     */
    @Column(name = "is_vpn_or_proxy", nullable = false)
    @Builder.Default
    private Boolean isVpnOrProxy = false;

    @Column(name = "vpn_detection_count", nullable = false)
    @Builder.Default
    private Integer vpnDetectionCount = 0;

    /**
     * Transaction Statistics
     */
    @Column(name = "total_transactions", nullable = false)
    @Builder.Default
    private Long totalTransactions = 0L;

    @Column(name = "first_seen_date", nullable = false)
    private LocalDateTime firstSeenDate;

    @Column(name = "last_seen_date")
    private LocalDateTime lastSeenDate;

    /**
     * Associated Users (for shared IP/network analysis)
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "location_associated_users",
        joinColumns = @JoinColumn(name = "location_profile_id"),
        indexes = @Index(name = "idx_location_user_mapping", columnList = "location_profile_id")
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
     * Fraud Timeline
     */
    @Column(name = "last_fraud_date")
    private LocalDateTime lastFraudDate;

    /**
     * IP Reputation Flags
     */
    @Column(name = "is_blacklisted")
    @Builder.Default
    private Boolean isBlacklisted = false;

    @Column(name = "blacklist_reason", length = 500)
    private String blacklistReason;

    @Column(name = "blacklist_date")
    private LocalDateTime blacklistDate;

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
        if (firstSeenDate == null) {
            firstSeenDate = LocalDateTime.now();
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
     * Helper method to check if IP is new (less than 5 transactions)
     */
    public boolean isNewIp() {
        return totalTransactions != null && totalTransactions < 5;
    }

    /**
     * Helper method to check if IP has high fraud rate (>10%)
     */
    public boolean hasHighFraudRate() {
        return fraudRate != null && fraudRate > 0.1;
    }

    /**
     * Helper method to blacklist IP
     */
    public void blacklist(String reason) {
        this.isBlacklisted = true;
        this.blacklistReason = reason;
        this.blacklistDate = LocalDateTime.now();
        this.currentRiskLevel = "CRITICAL";
    }

    /**
     * Helper method to remove from blacklist
     */
    public void removeFromBlacklist() {
        this.isBlacklisted = false;
        this.blacklistReason = null;
        this.blacklistDate = null;
    }
}
