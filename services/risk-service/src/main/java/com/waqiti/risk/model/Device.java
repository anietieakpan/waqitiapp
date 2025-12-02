package com.waqiti.risk.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Device Entity
 *
 * Stores device fingerprint and trust information including:
 * - Device identification
 * - Trust scoring
 * - Usage patterns
 * - Security attributes
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "devices")
public class Device {

    @Id
    private String id;

    @Indexed(unique = true)
    @NotBlank
    private String deviceId; // Device fingerprint hash

    @Indexed
    private String userId; // Primary user (can be associated with multiple users)

    // Device Information
    private String deviceType; // MOBILE, TABLET, DESKTOP, UNKNOWN
    private String platform; // IOS, ANDROID, WEB, etc.
    private String platformVersion;
    private String deviceModel;
    private String deviceManufacturer;
    private String browserType;
    private String browserVersion;
    private String userAgent;

    // Screen Information
    private Integer screenWidth;
    private Integer screenHeight;
    private Integer screenDensity;

    // Device Fingerprint
    private String fingerprintHash;
    private Map<String, String> fingerprintComponents;
    private Integer fingerprintInconsistencies;

    // Security Attributes
    @Builder.Default
    private Boolean isRooted = false;

    @Builder.Default
    private Boolean isJailbroken = false;

    @Builder.Default
    private Boolean isEmulator = false;

    @Builder.Default
    private Boolean hasVpn = false;

    @Builder.Default
    private Boolean hasProxy = false;

    private Boolean hasBiometricAuth;
    private Boolean hasScreenLock;

    // Trust Scoring
    @Min(0)
    @Max(1)
    @Builder.Default
    private Double trustScore = 0.5;

    private String trustLevel; // LOW, MEDIUM, HIGH

    @Builder.Default
    private Boolean isTrusted = false;

    private LocalDateTime trustedSince;
    private String trustedBy;

    // Usage Statistics
    @Builder.Default
    private Integer totalTransactions = 0;

    @Builder.Default
    private Integer successfulTransactions = 0;

    @Builder.Default
    private Integer failedTransactions = 0;

    @Builder.Default
    private Integer blockedTransactions = 0;

    private Double successRate;

    // Associated Users
    private Set<String> associatedUserIds;

    @Builder.Default
    private Integer userCount = 1;

    // Geographic History
    private List<String> usedCountries;
    private String primaryCountry;
    private String lastUsedCountry;

    // IP History
    private List<String> recentIpAddresses;
    private String lastUsedIp;

    // Risk Indicators
    private Set<String> riskFlags;

    @Builder.Default
    private Boolean hasHighRiskHistory = false;

    private Integer fraudAttempts;
    private LocalDateTime lastFraudAttemptAt;

    // Device Lifecycle
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime lastVerifiedAt;

    @Builder.Default
    private Integer daysSinceFirstSeen = 0;

    @Builder.Default
    private Integer daysSinceLastUsed = 0;

    // App Information (for mobile)
    private String appVersion;
    private String appBuildNumber;
    private Boolean isLatestAppVersion;

    // Network Information
    private String networkType; // WIFI, CELLULAR, ETHERNET
    private String carrier;
    private String vpnProvider;

    // Behavioral Patterns
    private Set<Integer> typicalUsageHours;
    private Map<String, Integer> hourlyUsageDistribution;

    // Machine Learning Features
    private Map<String, Double> deviceFeatures;

    // Audit Fields
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    private String createdBy;
    private String updatedBy;

    // Metadata
    private Map<String, Object> metadata;

    /**
     * Calculate trust score based on various factors
     */
    public void calculateTrustScore() {
        double score = 1.0;

        // Reduce trust for security issues
        if (isRooted || isJailbroken) {
            score -= 0.3;
        }
        if (isEmulator) {
            score -= 0.5;
        }
        if (hasVpn || hasProxy) {
            score -= 0.2;
        }

        // Reduce trust for multiple users
        if (userCount != null && userCount > 3) {
            score -= 0.3;
        }

        // Reduce trust for high fraud attempts
        if (fraudAttempts != null && fraudAttempts > 0) {
            score -= (fraudAttempts * 0.2);
        }

        // Reduce trust for low success rate
        if (successRate != null && successRate < 0.5) {
            score -= 0.2;
        }

        // Increase trust for long history
        if (daysSinceFirstSeen != null && daysSinceFirstSeen > 90) {
            score += 0.1;
        }

        // Increase trust for recent activity
        if (daysSinceLastUsed != null && daysSinceLastUsed < 7) {
            score += 0.05;
        }

        this.trustScore = Math.max(0.0, Math.min(1.0, score));

        // Set trust level
        if (this.trustScore >= 0.8) {
            this.trustLevel = "HIGH";
        } else if (this.trustScore >= 0.5) {
            this.trustLevel = "MEDIUM";
        } else {
            this.trustLevel = "LOW";
        }

        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Update success rate
     */
    public void updateSuccessRate() {
        int total = successfulTransactions + failedTransactions;
        if (total > 0) {
            this.successRate = (double) successfulTransactions / total;
        }
    }

    /**
     * Record transaction
     */
    public void recordTransaction(boolean successful, boolean blocked) {
        this.totalTransactions++;

        if (blocked) {
            this.blockedTransactions++;
        } else if (successful) {
            this.successfulTransactions++;
        } else {
            this.failedTransactions++;
        }

        updateSuccessRate();
        this.lastUsedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Add risk flag
     */
    public void addRiskFlag(String flag) {
        if (riskFlags == null) {
            riskFlags = new java.util.HashSet<>();
        }
        riskFlags.add(flag);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Check if device is high risk
     */
    public boolean isHighRisk() {
        return (trustScore != null && trustScore < 0.3) ||
               isEmulator ||
               (fraudAttempts != null && fraudAttempts > 2) ||
               hasHighRiskHistory;
    }

    /**
     * Associate with user
     */
    public void associateWithUser(String userId) {
        if (associatedUserIds == null) {
            associatedUserIds = new java.util.HashSet<>();
        }
        associatedUserIds.add(userId);
        this.userCount = associatedUserIds.size();
        this.updatedAt = LocalDateTime.now();
    }
}
