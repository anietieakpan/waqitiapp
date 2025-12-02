package com.waqiti.virtualcard.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Card Session Entity
 * Manages temporary card sessions for enhanced security and transaction tracking
 */
@Entity
@Table(name = "card_sessions",
    indexes = {
        @Index(name = "idx_card_id", columnList = "card_id"),
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_session_token", columnList = "session_token", unique = true),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_expires_at", columnList = "expires_at"),
        @Index(name = "idx_created_at", columnList = "created_at")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CardSession {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @EqualsAndHashCode.Include
    private String id;
    
    @Column(name = "card_id", nullable = false)
    private String cardId;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "session_token", nullable = false, unique = true, length = 256)
    private String sessionToken;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false, length = 30)
    private SessionType sessionType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SessionStatus status;
    
    @Column(name = "device_id", length = 100)
    private String deviceId;
    
    @Column(name = "device_type", length = 50)
    private String deviceType;
    
    @Column(name = "device_name", length = 100)
    private String deviceName;
    
    @Column(name = "device_os", length = 50)
    private String deviceOs;
    
    @Column(name = "device_os_version", length = 20)
    private String deviceOsVersion;
    
    @Column(name = "app_version", length = 20)
    private String appVersion;
    
    @Column(name = "source_ip", length = 45)
    private String sourceIp;
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    @Column(name = "location", length = 200)
    private String location;
    
    @Column(name = "latitude")
    private Double latitude;
    
    @Column(name = "longitude")
    private Double longitude;
    
    @Column(name = "country_code", length = 3)
    private String countryCode;
    
    @Column(name = "city", length = 100)
    private String city;
    
    @Column(name = "merchant_id", length = 50)
    private String merchantId;
    
    @Column(name = "merchant_name", length = 200)
    private String merchantName;
    
    @Column(name = "max_amount", precision = 19, scale = 2)
    private BigDecimal maxAmount;
    
    @Column(name = "currency", length = 3)
    private String currency;
    
    @Column(name = "transaction_count", nullable = false)
    private Integer transactionCount;
    
    @Column(name = "max_transactions")
    private Integer maxTransactions;
    
    @Column(name = "total_amount_used", precision = 19, scale = 2)
    private BigDecimal totalAmountUsed;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "activated_at")
    private LocalDateTime activatedAt;
    
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;
    
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    
    @Column(name = "terminated_at")
    private LocalDateTime terminatedAt;
    
    @Column(name = "termination_reason", length = 200)
    private String terminationReason;
    
    @Column(name = "authentication_method", length = 50)
    private String authenticationMethod;
    
    @Column(name = "authentication_level")
    private Integer authenticationLevel;
    
    @Column(name = "mfa_verified", nullable = false)
    private boolean mfaVerified;
    
    @Column(name = "biometric_verified", nullable = false)
    private boolean biometricVerified;
    
    @Column(name = "pin_verified", nullable = false)
    private boolean pinVerified;
    
    @Column(name = "risk_score")
    private Integer riskScore;
    
    @Column(name = "fraud_score")
    private Integer fraudScore;
    
    @Column(name = "is_suspicious", nullable = false)
    private boolean isSuspicious;
    
    @Column(name = "is_high_risk", nullable = false)
    private boolean isHighRisk;
    
    @Column(name = "requires_additional_auth", nullable = false)
    private boolean requiresAdditionalAuth;
    
    @Column(name = "challenge_token", length = 256)
    private String challengeToken;
    
    @Column(name = "challenge_expires_at")
    private LocalDateTime challengeExpiresAt;
    
    @Column(name = "challenge_attempts")
    private Integer challengeAttempts;
    
    @Column(name = "max_challenge_attempts")
    private Integer maxChallengeAttempts;
    
    @ElementCollection
    @CollectionTable(
        name = "session_transactions",
        joinColumns = @JoinColumn(name = "session_id")
    )
    @Column(name = "transaction_id")
    private List<String> transactionIds;
    
    @ElementCollection
    @CollectionTable(
        name = "session_metadata",
        joinColumns = @JoinColumn(name = "session_id")
    )
    @MapKeyColumn(name = "key")
    @Column(name = "value")
    private Map<String, String> metadata;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    /**
     * Session types
     */
    public enum SessionType {
        ONLINE_PURCHASE,
        IN_STORE_PURCHASE,
        ATM_WITHDRAWAL,
        CONTACTLESS_PAYMENT,
        RECURRING_PAYMENT,
        SUBSCRIPTION,
        INTERNATIONAL_TRANSACTION,
        HIGH_VALUE_TRANSACTION,
        MERCHANT_SPECIFIC,
        TEMPORARY_ACCESS,
        API_ACCESS,
        MOBILE_WALLET,
        WEB_CHECKOUT,
        VIRTUAL_TERMINAL,
        CARD_VERIFICATION
    }
    
    /**
     * Session status
     */
    public enum SessionStatus {
        PENDING,
        ACTIVE,
        EXPIRED,
        TERMINATED,
        SUSPENDED,
        COMPLETED,
        FAILED,
        LOCKED,
        CHALLENGE_REQUIRED
    }
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (sessionToken == null) {
            sessionToken = generateSessionToken();
        }
        if (transactionCount == null) {
            transactionCount = 0;
        }
        if (totalAmountUsed == null) {
            totalAmountUsed = BigDecimal.ZERO;
        }
        if (status == null) {
            status = SessionStatus.PENDING;
        }
        if (authenticationLevel == null) {
            authenticationLevel = 1;
        }
        if (challengeAttempts == null) {
            challengeAttempts = 0;
        }
        if (maxChallengeAttempts == null) {
            maxChallengeAttempts = 3;
        }
        if (transactionIds == null) {
            transactionIds = new ArrayList<>();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    /**
     * Generate unique session token
     */
    private String generateSessionToken() {
        return UUID.randomUUID().toString() + "-" + System.currentTimeMillis();
    }
    
    /**
     * Check if session is valid
     */
    public boolean isValid() {
        return status == SessionStatus.ACTIVE &&
               LocalDateTime.now().isBefore(expiresAt) &&
               !isLocked() &&
               !hasExceededLimits();
    }
    
    /**
     * Check if session is expired
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt) ||
               status == SessionStatus.EXPIRED;
    }
    
    /**
     * Check if session is locked
     */
    public boolean isLocked() {
        return status == SessionStatus.LOCKED ||
               status == SessionStatus.SUSPENDED ||
               status == SessionStatus.TERMINATED;
    }
    
    /**
     * Check if session has exceeded limits
     */
    public boolean hasExceededLimits() {
        if (maxTransactions != null && transactionCount >= maxTransactions) {
            return true;
        }
        if (maxAmount != null && totalAmountUsed.compareTo(maxAmount) >= 0) {
            return true;
        }
        return false;
    }
    
    /**
     * Check if session can process amount
     */
    public boolean canProcessAmount(BigDecimal amount) {
        if (maxAmount == null) {
            return true;
        }
        BigDecimal remaining = maxAmount.subtract(totalAmountUsed);
        return amount.compareTo(remaining) <= 0;
    }
    
    /**
     * Check if session requires challenge
     */
    public boolean requiresChallenge() {
        return status == SessionStatus.CHALLENGE_REQUIRED ||
               requiresAdditionalAuth ||
               (isHighRisk && !mfaVerified);
    }
    
    /**
     * Check if challenge is valid
     */
    public boolean isChallengeValid() {
        return challengeToken != null &&
               challengeExpiresAt != null &&
               LocalDateTime.now().isBefore(challengeExpiresAt) &&
               challengeAttempts < maxChallengeAttempts;
    }
    
    /**
     * Increment transaction count
     */
    public void incrementTransactionCount() {
        if (transactionCount == null) {
            transactionCount = 0;
        }
        transactionCount++;
        lastUsedAt = LocalDateTime.now();
    }
    
    /**
     * Add transaction amount
     */
    public void addTransactionAmount(BigDecimal amount) {
        if (totalAmountUsed == null) {
            totalAmountUsed = BigDecimal.ZERO;
        }
        totalAmountUsed = totalAmountUsed.add(amount);
        lastUsedAt = LocalDateTime.now();
    }
    
    /**
     * Add transaction ID
     */
    public void addTransactionId(String transactionId) {
        if (transactionIds == null) {
            transactionIds = new ArrayList<>();
        }
        transactionIds.add(transactionId);
    }
    
    /**
     * Increment challenge attempts
     */
    public void incrementChallengeAttempts() {
        if (challengeAttempts == null) {
            challengeAttempts = 0;
        }
        challengeAttempts++;
    }
    
    /**
     * Check if session is for high-value transaction
     */
    public boolean isHighValueSession() {
        return sessionType == SessionType.HIGH_VALUE_TRANSACTION ||
               (maxAmount != null && maxAmount.compareTo(new BigDecimal("1000")) > 0);
    }
    
    /**
     * Get remaining amount
     */
    public BigDecimal getRemainingAmount() {
        if (maxAmount == null) {
            return null;
        }
        return maxAmount.subtract(totalAmountUsed).max(BigDecimal.ZERO);
    }
    
    /**
     * Get remaining transactions
     */
    public Integer getRemainingTransactions() {
        if (maxTransactions == null) {
            return null;
        }
        return Math.max(0, maxTransactions - transactionCount);
    }
    
    /**
     * Get session duration in minutes
     */
    public long getSessionDurationMinutes() {
        if (createdAt == null) {
            return 0;
        }
        LocalDateTime endTime = terminatedAt != null ? terminatedAt : LocalDateTime.now();
        return java.time.Duration.between(createdAt, endTime).toMinutes();
    }
}