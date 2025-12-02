package com.waqiti.crypto.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Crypto Transaction Pattern Entity
 * 
 * Stores cryptocurrency transaction patterns for behavioral analysis and fraud detection.
 * Tracks user behavior, device usage, location, and risk indicators.
 * 
 * @author Waqiti Crypto Security Team
 * @version 1.0 - Production Implementation
 */
@Entity
@Table(name = "crypto_transaction_patterns",
       indexes = {
           @Index(name = "idx_crypto_pattern_user_id", columnList = "user_id"),
           @Index(name = "idx_crypto_pattern_timestamp", columnList = "timestamp DESC"),
           @Index(name = "idx_crypto_pattern_user_timestamp", columnList = "user_id, timestamp DESC"),
           @Index(name = "idx_crypto_pattern_to_address", columnList = "to_address"),
           @Index(name = "idx_crypto_pattern_from_address", columnList = "from_address"),
           @Index(name = "idx_crypto_pattern_device", columnList = "device_fingerprint"),
           @Index(name = "idx_crypto_pattern_risk", columnList = "risk_score DESC"),
           @Index(name = "idx_crypto_pattern_country", columnList = "country_code")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CryptoTransactionPatternEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private String transactionId;

    @Column(name = "transaction_type", length = 20)
    private String transactionType; // SEND, RECEIVE, SWAP, STAKE

    @Column(name = "currency", length = 10, nullable = false)
    private String currency;

    @Column(name = "amount", precision = 30, scale = 18, nullable = false)
    private BigDecimal amount;

    @Column(name = "amount_usd", precision = 19, scale = 4)
    private BigDecimal amountUsd;

    @Column(name = "from_address", length = 255)
    private String fromAddress;

    @Column(name = "to_address", length = 255)
    private String toAddress;

    @CreationTimestamp
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "hour_of_day")
    private Integer hourOfDay;

    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    @Column(name = "is_weekend")
    private Boolean isWeekend;

    @Column(name = "is_night_time")
    private Boolean isNightTime;

    @Column(name = "device_fingerprint", length = 255)
    private String deviceFingerprint;

    @Column(name = "device_type", length = 50)
    private String deviceType; // MOBILE, DESKTOP, TABLET

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "latitude", precision = 10, scale = 7)
    private Double latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private Double longitude;

    @Column(name = "is_international")
    private Boolean isInternational;

    @Column(name = "is_high_value")
    private Boolean isHighValue;

    @Column(name = "is_round_amount")
    private Boolean isRoundAmount;

    @Column(name = "risk_score", precision = 5, scale = 2)
    private Double riskScore;

    @Column(name = "address_risk_score", precision = 5, scale = 2)
    private Double addressRiskScore;

    @Column(name = "behavioral_risk_score", precision = 5, scale = 2)
    private Double behavioralRiskScore;

    @Column(name = "velocity_risk_score", precision = 5, scale = 2)
    private Double velocityRiskScore;

    @Column(name = "pattern_risk_score", precision = 5, scale = 2)
    private Double patternRiskScore;

    @Column(name = "amount_risk_score", precision = 5, scale = 2)
    private Double amountRiskScore;

    @Column(name = "fraud_probability", precision = 5, scale = 4)
    private Double fraudProbability;

    @Column(name = "is_sanctioned_address")
    private Boolean isSanctionedAddress;

    @Column(name = "is_mixer_address")
    private Boolean isMixerAddress;

    @Column(name = "is_dark_market_address")
    private Boolean isDarkMarketAddress;

    @Column(name = "is_gambling_address")
    private Boolean isGamblingAddress;

    @Column(name = "is_ransomware_address")
    private Boolean isRansomwareAddress;

    @Column(name = "is_new_address")
    private Boolean isNewAddress;

    @Column(name = "is_exchange_address")
    private Boolean isExchangeAddress;

    @Column(name = "has_risky_connections")
    private Boolean hasRiskyConnections;

    @Column(name = "network_risk_level", length = 20)
    private String networkRiskLevel; // LOW, MEDIUM, HIGH, CRITICAL

    @Column(name = "recommended_action", length = 50)
    private String recommendedAction; // ALLOW, MONITOR, ADDITIONAL_VERIFICATION, MANUAL_REVIEW, BLOCK

    @Column(name = "flagged_for_review")
    private Boolean flaggedForReview;

    @Column(name = "reviewed")
    private Boolean reviewed;

    @Column(name = "reviewer_notes", columnDefinition = "TEXT")
    private String reviewerNotes;

    @Column(name = "analysis_timestamp")
    private LocalDateTime analysisTimestamp;

    @Column(name = "ml_model_version", length = 50)
    private String mlModelVersion;

    @Column(name = "blockchain_confirmations")
    private Integer blockchainConfirmations;

    @Column(name = "transaction_fee", precision = 30, scale = 18)
    private BigDecimal transactionFee;

    @Column(name = "transaction_fee_usd", precision = 19, scale = 4)
    private BigDecimal transactionFeeUsd;

    @Column(name = "gas_used")
    private Long gasUsed;

    @Column(name = "gas_price", precision = 30, scale = 18)
    private BigDecimal gasPrice;

    @Column(name = "block_number")
    private Long blockNumber;

    @Column(name = "blockchain_timestamp")
    private LocalDateTime blockchainTimestamp;

    @Type(com.vladmihalcea.hibernate.type.json.JsonType.class)
    @Column(name = "risk_factors", columnDefinition = "jsonb")
    private Map<String, Object> riskFactors;

    @Type(com.vladmihalcea.hibernate.type.json.JsonType.class)
    @Column(name = "extended_metadata", columnDefinition = "jsonb")
    private Map<String, Object> extendedMetadata;

    @PrePersist
    protected void initializeDerivedFields() {
        if (timestamp != null) {
            this.hourOfDay = timestamp.getHour();
            this.dayOfWeek = timestamp.getDayOfWeek().getValue();
            this.isWeekend = (dayOfWeek == 6 || dayOfWeek == 7);
            this.isNightTime = (hourOfDay >= 22 || hourOfDay <= 6);
        }
        
        if (amount != null) {
            this.isHighValue = amountUsd != null && amountUsd.compareTo(BigDecimal.valueOf(10000)) > 0;
            this.isRoundAmount = isRoundNumber(amount);
        }
        
        if (analysisTimestamp == null) {
            this.analysisTimestamp = LocalDateTime.now();
        }
        
        if (riskFactors == null) {
            this.riskFactors = new HashMap<>();
        }
        
        if (extendedMetadata == null) {
            this.extendedMetadata = new HashMap<>();
        }
        
        if (flaggedForReview == null) {
            this.flaggedForReview = false;
        }
        
        if (reviewed == null) {
            this.reviewed = false;
        }
    }

    private boolean isRoundNumber(BigDecimal amount) {
        if (amount == null) return false;
        
        BigDecimal[] roundThresholds = {
            BigDecimal.valueOf(1000),
            BigDecimal.valueOf(500),
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(50),
            BigDecimal.valueOf(10)
        };
        
        for (BigDecimal threshold : roundThresholds) {
            if (amount.remainder(threshold).compareTo(BigDecimal.ZERO) == 0) {
                return true;
            }
        }
        
        return false;
    }

    public void addRiskFactor(String factorName, Object factorValue) {
        if (riskFactors == null) {
            riskFactors = new HashMap<>();
        }
        riskFactors.put(factorName, factorValue);
    }

    public void addMetadata(String key, Object value) {
        if (extendedMetadata == null) {
            extendedMetadata = new HashMap<>();
        }
        extendedMetadata.put(key, value);
    }

    public boolean isHighRisk() {
        return riskScore != null && riskScore >= 60.0;
    }

    public boolean isCriticalRisk() {
        return riskScore != null && riskScore >= 80.0;
    }

    public boolean isSuspicious() {
        return Boolean.TRUE.equals(flaggedForReview) ||
               Boolean.TRUE.equals(isSanctionedAddress) ||
               Boolean.TRUE.equals(isMixerAddress) ||
               Boolean.TRUE.equals(isDarkMarketAddress) ||
               Boolean.TRUE.equals(isRansomwareAddress) ||
               isHighRisk();
    }
}