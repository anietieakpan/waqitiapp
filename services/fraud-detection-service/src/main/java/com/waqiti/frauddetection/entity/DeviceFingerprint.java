package com.waqiti.frauddetection.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Device Fingerprint Entity
 *
 * Stores comprehensive device fingerprinting data for fraud detection and device tracking.
 * Captures browser fingerprints, device characteristics, network information, and behavioral
 * markers to uniquely identify devices across sessions.
 *
 * PRODUCTION-GRADE ENTITY
 * - Optimistic locking with @Version
 * - JPA Auditing for created/modified timestamps
 * - Strategic indexing for performance
 * - Unique constraint on fingerprint hash
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0
 */
@Entity
@Table(name = "device_fingerprints", indexes = {
    @Index(name = "idx_fingerprint_hash", columnList = "fingerprint_hash", unique = true),
    @Index(name = "idx_device_user", columnList = "user_id"),
    @Index(name = "idx_device_created", columnList = "created_at"),
    @Index(name = "idx_device_ip", columnList = "ip_address"),
    @Index(name = "idx_device_risk", columnList = "risk_score")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceFingerprint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    private Long version;

    /**
     * Unique fingerprint hash (SHA-256)
     */
    @Column(name = "fingerprint_hash", nullable = false, unique = true, length = 64)
    private String fingerprintHash;

    /**
     * User ID associated with this device
     */
    @Column(name = "user_id", length = 100)
    private String userId;

    /**
     * Browser Information
     */
    @Column(name = "user_agent", length = 1000)
    private String userAgent;

    @Column(name = "browser_name", length = 100)
    private String browserName;

    @Column(name = "browser_version", length = 50)
    private String browserVersion;

    /**
     * Device Information
     */
    @Column(name = "device_type", length = 50)
    private String deviceType; // mobile, tablet, desktop

    @Column(name = "operating_system", length = 100)
    private String operatingSystem;

    @Column(name = "os_version", length = 50)
    private String osVersion;

    @Column(name = "device_vendor", length = 100)
    private String deviceVendor;

    @Column(name = "device_model", length = 100)
    private String deviceModel;

    /**
     * Network Information
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "ip_country", length = 2)
    private String ipCountry;

    @Column(name = "ip_city", length = 100)
    private String ipCity;

    @Column(name = "ip_isp", length = 200)
    private String ipIsp;

    /**
     * Browser Fingerprint Data
     */
    @Column(name = "screen_resolution", length = 50)
    private String screenResolution;

    @Column(name = "timezone", length = 50)
    private String timezone;

    @Column(name = "language", length = 50)
    private String language;

    @Column(name = "canvas_fingerprint", length = 64)
    private String canvasFingerprint;

    @Column(name = "webgl_fingerprint", length = 64)
    private String webglFingerprint;

    @Column(name = "fonts_fingerprint", length = 64)
    private String fontsFingerprint;

    @Column(name = "plugins_fingerprint", length = 64)
    private String pluginsFingerprint;

    /**
     * Additional Attributes
     */
    @Column(name = "cookies_enabled")
    private Boolean cookiesEnabled;

    @Column(name = "javascript_enabled")
    private Boolean javascriptEnabled;

    @Column(name = "do_not_track")
    private Boolean doNotTrack;

    @Column(name = "touch_support")
    private Boolean touchSupport;

    /**
     * Risk Metrics
     */
    @Column(name = "risk_score", precision = 5, scale = 4)
    private Double riskScore;

    @Column(name = "is_vpn")
    private Boolean isVpn;

    @Column(name = "is_proxy")
    private Boolean isProxy;

    @Column(name = "is_tor")
    private Boolean isTor;

    @Column(name = "is_bot")
    private Boolean isBot;

    /**
     * Usage Statistics
     */
    @Column(name = "first_seen")
    private LocalDateTime firstSeen;

    @Column(name = "last_seen")
    private LocalDateTime lastSeen;

    @Column(name = "usage_count")
    @Builder.Default
    private Long usageCount = 0L;

    @Column(name = "fraud_count")
    @Builder.Default
    private Long fraudCount = 0L;

    /**
     * Audit Fields
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Check if device is flagged as high risk
     */
    public boolean isHighRisk() {
        return riskScore != null && riskScore > 0.7;
    }

    /**
     * Check if device uses anonymization tools
     */
    public boolean usesAnonymization() {
        return Boolean.TRUE.equals(isVpn) ||
               Boolean.TRUE.equals(isProxy) ||
               Boolean.TRUE.equals(isTor);
    }

    /**
     * Check if device has fraud history
     */
    public boolean hasFraudHistory() {
        return fraudCount != null && fraudCount > 0;
    }

    /**
     * Calculate fraud rate
     */
    public double getFraudRate() {
        if (usageCount == null || usageCount == 0) {
            return 0.0;
        }
        return (fraudCount == null ? 0 : fraudCount.doubleValue()) / usageCount.doubleValue();
    }

    /**
     * Increment usage count
     */
    public void incrementUsageCount() {
        this.usageCount = (usageCount == null ? 0 : usageCount) + 1;
        this.lastSeen = LocalDateTime.now();
    }

    /**
     * Increment fraud count
     */
    public void incrementFraudCount() {
        this.fraudCount = (fraudCount == null ? 0 : fraudCount) + 1;
        recalculateRiskScore();
    }

    /**
     * Recalculate risk score based on fraud history
     */
    private void recalculateRiskScore() {
        double baseScore = this.riskScore != null ? this.riskScore : 0.0;
        double fraudRate = getFraudRate();

        // Increase score based on fraud rate
        double newScore = Math.min(1.0, baseScore + (fraudRate * 0.5));

        // Additional risk for anonymization tools
        if (usesAnonymization()) {
            newScore = Math.min(1.0, newScore + 0.2);
        }

        // Additional risk for bots
        if (Boolean.TRUE.equals(isBot)) {
            newScore = Math.min(1.0, newScore + 0.3);
        }

        this.riskScore = newScore;
    }
}
