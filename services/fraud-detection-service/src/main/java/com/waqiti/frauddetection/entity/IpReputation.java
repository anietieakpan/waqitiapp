package com.waqiti.frauddetection.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity for tracking IP address reputation and fraud indicators
 * Maintains blacklists, whitelists, and reputation scores for IP addresses
 */
@Entity
@Table(name = "ip_reputation", indexes = {
        @Index(name = "idx_ip_reputation_ip_address", columnList = "ip_address", unique = true),
        @Index(name = "idx_ip_reputation_score", columnList = "reputation_score"),
        @Index(name = "idx_ip_reputation_blacklisted", columnList = "blacklisted"),
        @Index(name = "idx_ip_reputation_country", columnList = "country"),
        @Index(name = "idx_ip_reputation_last_updated", columnList = "last_updated DESC")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IpReputation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "ip_address", nullable = false, unique = true, length = 45)
    private String ipAddress;

    @Column(name = "reputation_score", precision = 5, scale = 2)
    @Builder.Default
    private Double reputationScore = 50.0; // Default neutral score (0-100 scale)

    @Column(name = "blacklisted", nullable = false)
    @Builder.Default
    private Boolean blacklisted = false;

    @Column(name = "whitelisted", nullable = false)
    @Builder.Default
    private Boolean whitelisted = false;

    @Column(name = "fraud_incident_count", nullable = false)
    @Builder.Default
    private Integer fraudIncidentCount = 0;

    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    @Column(name = "last_failed_login")
    private LocalDateTime lastFailedLogin;

    @Column(name = "successful_transactions", nullable = false)
    @Builder.Default
    private Integer successfulTransactions = 0;

    @Column(name = "total_transaction_count", nullable = false)
    @Builder.Default
    private Integer totalTransactionCount = 0;

    @Column(name = "country", length = 2)
    private String country; // ISO 2-letter country code

    @Column(name = "country_name", length = 100)
    private String countryName;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "region", length = 100)
    private String region;

    @Column(name = "isp", length = 200)
    private String isp;

    @Column(name = "organization", length = 200)
    private String organization;

    @Column(name = "is_vpn", nullable = false)
    @Builder.Default
    private Boolean isVpn = false;

    @Column(name = "is_proxy", nullable = false)
    @Builder.Default
    private Boolean isProxy = false;

    @Column(name = "is_tor", nullable = false)
    @Builder.Default
    private Boolean isTor = false;

    @Column(name = "is_datacenter", nullable = false)
    @Builder.Default
    private Boolean isDatacenter = false;

    @Column(name = "is_anonymous", nullable = false)
    @Builder.Default
    private Boolean isAnonymous = false;

    @Column(name = "threat_level", length = 20)
    private String threatLevel; // LOW, MEDIUM, HIGH, CRITICAL

    @Column(name = "first_seen", nullable = false)
    @CreationTimestamp
    private LocalDateTime firstSeen;

    @Column(name = "last_seen")
    private LocalDateTime lastSeen;

    @Column(name = "last_updated", nullable = false)
    @UpdateTimestamp
    private LocalDateTime lastUpdated;

    @Column(name = "blacklist_reason", length = 500)
    private String blacklistReason;

    @Column(name = "blacklisted_at")
    private LocalDateTime blacklistedAt;

    @Column(name = "blacklisted_by")
    private String blacklistedBy;

    @Column(name = "whitelist_reason", length = 500)
    private String whitelistReason;

    @Column(name = "whitelisted_at")
    private LocalDateTime whitelistedAt;

    @Column(name = "whitelisted_by")
    private String whitelistedBy;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Version
    private Long version;

    /**
     * Calculate and update reputation score based on fraud incidents and successful transactions
     */
    public void recalculateReputationScore() {
        if (this.totalTransactionCount == 0) {
            this.reputationScore = 50.0; // Neutral score for new IPs
            return;
        }

        // Base score from success rate
        double successRate = (double) this.successfulTransactions / this.totalTransactionCount;
        double baseScore = successRate * 100.0;

        // Penalize for fraud incidents (each incident reduces score)
        double fraudPenalty = this.fraudIncidentCount * 10.0;

        // Penalize for failed logins
        double loginPenalty = Math.min(this.failedLoginAttempts * 2.0, 20.0);

        // Penalize for VPN/Proxy/Tor usage
        double anonymityPenalty = 0.0;
        if (Boolean.TRUE.equals(this.isVpn)) anonymityPenalty += 5.0;
        if (Boolean.TRUE.equals(this.isProxy)) anonymityPenalty += 5.0;
        if (Boolean.TRUE.equals(this.isTor)) anonymityPenalty += 15.0;
        if (Boolean.TRUE.equals(this.isDatacenter)) anonymityPenalty += 10.0;

        // Calculate final score (clamped between 0 and 100)
        this.reputationScore = Math.max(0.0, Math.min(100.0,
            baseScore - fraudPenalty - loginPenalty - anonymityPenalty));

        // Auto-update threat level based on score
        updateThreatLevel();
    }

    /**
     * Update threat level based on reputation score
     */
    private void updateThreatLevel() {
        if (this.reputationScore >= 75.0) {
            this.threatLevel = "LOW";
        } else if (this.reputationScore >= 50.0) {
            this.threatLevel = "MEDIUM";
        } else if (this.reputationScore >= 25.0) {
            this.threatLevel = "HIGH";
        } else {
            this.threatLevel = "CRITICAL";
        }
    }

    /**
     * Increment fraud incident count and recalculate reputation
     */
    public void recordFraudIncident() {
        this.fraudIncidentCount++;
        recalculateReputationScore();
    }

    /**
     * Record failed login attempt
     */
    public void recordFailedLogin() {
        this.failedLoginAttempts++;
        this.lastFailedLogin = LocalDateTime.now();
        recalculateReputationScore();
    }

    /**
     * Record successful transaction
     */
    public void recordSuccessfulTransaction() {
        this.successfulTransactions++;
        this.totalTransactionCount++;
        this.lastSeen = LocalDateTime.now();
        recalculateReputationScore();
    }

    /**
     * Blacklist this IP address
     */
    public void blacklist(String reason, String blacklistedBy) {
        this.blacklisted = true;
        this.blacklistReason = reason;
        this.blacklistedAt = LocalDateTime.now();
        this.blacklistedBy = blacklistedBy;
        this.reputationScore = 0.0;
        this.threatLevel = "CRITICAL";
    }

    /**
     * Whitelist this IP address
     */
    public void whitelist(String reason, String whitelistedBy) {
        this.whitelisted = true;
        this.whitelistReason = reason;
        this.whitelistedAt = LocalDateTime.now();
        this.whitelistedBy = whitelistedBy;
        this.blacklisted = false;
        this.reputationScore = 100.0;
        this.threatLevel = "LOW";
    }

    /**
     * Remove from blacklist
     */
    public void removeFromBlacklist() {
        this.blacklisted = false;
        this.blacklistReason = null;
        this.blacklistedAt = null;
        this.blacklistedBy = null;
        recalculateReputationScore();
    }

    /**
     * Remove from whitelist
     */
    public void removeFromWhitelist() {
        this.whitelisted = false;
        this.whitelistReason = null;
        this.whitelistedAt = null;
        this.whitelistedBy = null;
        recalculateReputationScore();
    }
}
