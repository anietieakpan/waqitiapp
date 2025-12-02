package com.waqiti.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "fraud_check_records",
    indexes = {
        @Index(name = "idx_fraud_check_transaction_id", columnList = "transaction_id"),
        @Index(name = "idx_fraud_check_user_id", columnList = "user_id"),
        @Index(name = "idx_fraud_check_risk_level", columnList = "risk_level"),
        @Index(name = "idx_fraud_check_created_at", columnList = "created_at"),
        @Index(name = "idx_fraud_check_approved", columnList = "approved"),
        @Index(name = "idx_fraud_check_manual_review", columnList = "requires_manual_review")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCheckRecord {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    
    @Column(name = "transaction_id", nullable = false)
    private String transactionId;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "account_id")
    private String accountId;
    
    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;
    
    @Column(name = "currency", length = 3)
    private String currency;
    
    @Column(name = "transaction_type", length = 50)
    private String transactionType;
    
    @Column(name = "source_ip_address", length = 45)
    private String sourceIpAddress;
    
    @Column(name = "device_id")
    private String deviceId;
    
    @Column(name = "device_fingerprint", columnDefinition = "TEXT")
    private String deviceFingerprint;
    
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;
    
    @Column(name = "geolocation")
    private String geolocation;
    
    @Column(name = "risk_level", length = 20, nullable = false)
    private String riskLevel;
    
    @Column(name = "risk_score", precision = 5, scale = 4, nullable = false)
    private Double riskScore;
    
    @Column(name = "ml_model_score", precision = 5, scale = 4)
    private Double mlModelScore;
    
    @Column(name = "ml_model_version", length = 50)
    private String mlModelVersion;
    
    @Column(name = "confidence", precision = 5, scale = 4)
    private Double confidence;
    
    @Column(name = "approved", nullable = false)
    private Boolean approved;
    
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;
    
    @Column(name = "rules_triggered", columnDefinition = "TEXT")
    private String rulesTriggered;
    
    @Column(name = "recommendations", columnDefinition = "TEXT")
    private String recommendations;
    
    @Column(name = "requires_manual_review", nullable = false)
    private Boolean requiresManualReview;
    
    @Column(name = "requires_enhanced_monitoring", nullable = false)
    private Boolean requiresEnhancedMonitoring;
    
    @Column(name = "fallback_used", nullable = false)
    private Boolean fallbackUsed;
    
    @Column(name = "review_url")
    private String reviewUrl;
    
    @Column(name = "review_priority")
    private Integer reviewPriority;
    
    @Column(name = "beneficiary_id")
    private String beneficiaryId;
    
    @Column(name = "known_device")
    private Boolean knownDevice;
    
    @Column(name = "trusted_location")
    private Boolean trustedLocation;
    
    @Column(name = "failed_attempts")
    private Integer failedAttempts;
    
    @Column(name = "payment_method", length = 50)
    private String paymentMethod;
    
    @Column(name = "merchant_id")
    private String merchantId;
    
    @Column(name = "velocity_check_passed")
    private Boolean velocityCheckPassed;
    
    @Column(name = "sanction_check_passed")
    private Boolean sanctionCheckPassed;
    
    @Column(name = "device_reputation_score", precision = 5, scale = 4)
    private Double deviceReputationScore;
    
    @Column(name = "ip_reputation_score", precision = 5, scale = 4)
    private Double ipReputationScore;
    
    @Column(name = "behavioral_score", precision = 5, scale = 4)
    private Double behavioralScore;
    
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;
    
    @Column(name = "external_provider")
    private String externalProvider;
    
    @Column(name = "external_check_id")
    private String externalCheckId;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (approved == null) {
            approved = false;
        }
        if (requiresManualReview == null) {
            requiresManualReview = false;
        }
        if (requiresEnhancedMonitoring == null) {
            requiresEnhancedMonitoring = false;
        }
        if (fallbackUsed == null) {
            fallbackUsed = false;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}