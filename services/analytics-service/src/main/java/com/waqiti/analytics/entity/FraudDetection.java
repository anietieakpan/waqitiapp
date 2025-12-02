package com.waqiti.analytics.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing fraud detection events and analysis
 */
@Entity
@Table(name = "fraud_detection_events", indexes = {
    @Index(name = "idx_fraud_user", columnList = "userId"),
    @Index(name = "idx_fraud_transaction", columnList = "transactionId"),
    @Index(name = "idx_fraud_status", columnList = "status"),
    @Index(name = "idx_fraud_risk_level", columnList = "riskLevel"),
    @Index(name = "idx_fraud_detection_date", columnList = "detectionDate")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class FraudDetection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true)
    private UUID transactionId;

    @Column(nullable = false)
    private BigDecimal transactionAmount;

    @Column(nullable = false)
    private LocalDateTime detectionDate;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    @Column(nullable = false)
    private Double riskScore; // 0.0 to 100.0

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private FraudType fraudType;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DetectionStatus status;

    @Column(columnDefinition = "TEXT")
    private String detectionReasons;

    @Column(columnDefinition = "TEXT")
    private String anomalyDetails;

    @Column
    private String sourceIpAddress;

    @Column
    private String deviceFingerprint;

    @Column
    private String merchantName;

    @Column
    private String merchantCategory;

    @Column
    private String location;

    @Column
    private Boolean isInternational;

    @Column
    private Integer velocityCount; // Number of recent transactions

    @Column
    private BigDecimal velocityAmount; // Total amount in velocity window

    @Column
    @Enumerated(EnumType.STRING)
    private ActionTaken actionTaken;

    @Column
    private LocalDateTime actionDate;

    @Column
    private String actionBy;

    @Column
    private Boolean falsePositive;

    @Column(columnDefinition = "TEXT")
    private String investigationNotes;

    @Column
    private LocalDateTime resolvedDate;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum RiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum FraudType {
        CARD_NOT_PRESENT,
        ACCOUNT_TAKEOVER,
        IDENTITY_THEFT,
        FRIENDLY_FRAUD,
        MERCHANT_FRAUD,
        VELOCITY_ABUSE,
        LOCATION_ANOMALY,
        BEHAVIORAL_ANOMALY,
        DEVICE_ANOMALY,
        NETWORK_ANOMALY,
        UNKNOWN
    }

    public enum DetectionStatus {
        DETECTED, INVESTIGATING, CONFIRMED, REJECTED, RESOLVED
    }

    public enum ActionTaken {
        BLOCKED, CHALLENGED, ALLOWED, MANUAL_REVIEW, ESCALATED
    }
}