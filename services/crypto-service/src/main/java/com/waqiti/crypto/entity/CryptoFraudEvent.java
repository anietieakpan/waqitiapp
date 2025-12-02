/**
 * Crypto Fraud Event Entity
 * JPA entity representing cryptocurrency fraud detection events
 */
package com.waqiti.crypto.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "crypto_fraud_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CryptoFraudEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private CryptoCurrency currency;

    @Column(name = "amount", nullable = false, precision = 36, scale = 18)
    private BigDecimal amount;

    @Column(name = "to_address")
    private String toAddress;

    @Column(name = "risk_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false)
    private RiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommended_action", nullable = false)
    private RecommendedAction recommendedAction;

    @Column(name = "risk_factors", columnDefinition = "TEXT")
    private String riskFactors;

    @Column(name = "ip_address")
    private InetAddress ipAddress;

    @Column(name = "device_fingerprint")
    private String deviceFingerprint;

    @Column(name = "fraud_type", length = 50)
    private String fraudType;

    @Column(name = "confidence_level", precision = 3, scale = 2)
    private BigDecimal confidenceLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "investigation_status")
    @Builder.Default
    private InvestigationStatus investigationStatus = InvestigationStatus.OPEN;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Version
    private Long version;
    // Convenience methods for investigation workflow
    public void startInvestigation() {
        this.investigationStatus = InvestigationStatus.INVESTIGATING;
    }

    public void resolve(String notes) {
        this.investigationStatus = InvestigationStatus.RESOLVED;
        this.resolutionNotes = notes;
        this.resolvedAt = LocalDateTime.now();
    }

    public void markAsFalsePositive(String notes) {
        this.investigationStatus = InvestigationStatus.FALSE_POSITIVE;
        this.resolutionNotes = notes;
        this.resolvedAt = LocalDateTime.now();
    }

    public boolean isHighRisk() {
        return this.riskLevel == RiskLevel.HIGH || this.riskLevel == RiskLevel.CRITICAL;
    }

    public boolean requiresBlocking() {
        return this.recommendedAction == RecommendedAction.BLOCK;
    }

    public boolean requiresManualReview() {
        return this.recommendedAction == RecommendedAction.MANUAL_REVIEW;
    }

    // Enum for investigation status
    public enum InvestigationStatus {
        OPEN,
        INVESTIGATING,
        RESOLVED,
        FALSE_POSITIVE
    }
}