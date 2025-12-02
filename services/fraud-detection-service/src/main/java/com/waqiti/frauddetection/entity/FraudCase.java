package com.waqiti.frauddetection.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fraud Case Entity
 *
 * Represents a comprehensive fraud investigation case with evidence, analysis,
 * decision history, and resolution tracking. Used for ML model training and
 * fraud pattern analysis.
 *
 * PRODUCTION-GRADE ENTITY
 * - Optimistic locking with @Version
 * - JPA Auditing for timestamps
 * - Strategic indexing for query performance
 * - Comprehensive evidence tracking
 * - ML training data support
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0
 */
@Entity
@Table(name = "fraud_cases", indexes = {
    @Index(name = "idx_fraud_case_id", columnList = "case_id", unique = true),
    @Index(name = "idx_fraud_user_id", columnList = "user_id"),
    @Index(name = "idx_fraud_transaction_id", columnList = "transaction_id"),
    @Index(name = "idx_fraud_status", columnList = "status"),
    @Index(name = "idx_fraud_decision", columnList = "final_decision"),
    @Index(name = "idx_fraud_created", columnList = "created_at"),
    @Index(name = "idx_fraud_confirmed", columnList = "confirmed_fraud"),
    @Index(name = "idx_fraud_risk", columnList = "final_risk_score")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    private Long version;

    /**
     * Unique case identifier
     */
    @Column(name = "case_id", unique = true, nullable = false, length = 100)
    private String caseId;

    /**
     * Associated Transaction/User Information
     */
    @Column(name = "transaction_id", nullable = false, length = 100)
    private String transactionId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "merchant_id", length = 100)
    private String merchantId;

    /**
     * Transaction Details
     */
    @Column(name = "transaction_amount", precision = 19, scale = 4)
    private BigDecimal transactionAmount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "transaction_type", length = 50)
    private String transactionType;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    /**
     * Case Status
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private CaseStatus status = CaseStatus.OPEN;

    /**
     * Risk Assessment
     */
    @Column(name = "initial_risk_score", precision = 5, scale = 4)
    private Double initialRiskScore;

    @Column(name = "ml_risk_score", precision = 5, scale = 4)
    private Double mlRiskScore;

    @Column(name = "rule_risk_score", precision = 5, scale = 4)
    private Double ruleRiskScore;

    @Column(name = "final_risk_score", precision = 5, scale = 4)
    private Double finalRiskScore;

    /**
     * Fraud Decision
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "final_decision", length = 30)
    private FraudCaseDecision finalDecision;

    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;

    @Column(name = "auto_decided", nullable = false)
    @Builder.Default
    private Boolean autoDecided = false;

    /**
     * Ground Truth (for ML training)
     */
    @Column(name = "confirmed_fraud")
    private Boolean confirmedFraud;

    @Column(name = "fraud_type", length = 100)
    private String fraudType;

    @Column(name = "fraud_category", length = 100)
    private String fraudCategory;

    /**
     * Triggered Rules and Patterns
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "fraud_case_triggered_rules",
        joinColumns = @JoinColumn(name = "fraud_case_id")
    )
    @Column(name = "rule_id", length = 100)
    @Builder.Default
    private List<String> triggeredRules = new ArrayList<>();

    @Column(name = "triggered_rules_count")
    @Builder.Default
    private Integer triggeredRulesCount = 0;

    /**
     * Evidence and Analysis
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "fraud_case_evidence",
        joinColumns = @JoinColumn(name = "fraud_case_id")
    )
    @MapKeyColumn(name = "evidence_key", length = 100)
    @Column(name = "evidence_value", columnDefinition = "TEXT")
    @Builder.Default
    private Map<String, String> evidence = new HashMap<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "fraud_case_features",
        joinColumns = @JoinColumn(name = "fraud_case_id")
    )
    @MapKeyColumn(name = "feature_name", length = 100)
    @Column(name = "feature_value")
    @Builder.Default
    private Map<String, Double> mlFeatures = new HashMap<>();

    /**
     * Device and Location Information
     */
    @Column(name = "device_fingerprint_hash", length = 64)
    private String deviceFingerprintHash;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "city", length = 100)
    private String city;

    /**
     * Investigation Details
     */
    @Column(name = "investigated_by", length = 100)
    private String investigatedBy;

    @Column(name = "investigation_started_at")
    private LocalDateTime investigationStartedAt;

    @Column(name = "investigation_completed_at")
    private LocalDateTime investigationCompletedAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    /**
     * Model Information
     */
    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Column(name = "model_name", length = 100)
    private String modelName;

    /**
     * False Positive Tracking
     */
    @Column(name = "false_positive")
    private Boolean falsePositive;

    @Column(name = "false_positive_reason", columnDefinition = "TEXT")
    private String falsePositiveReason;

    /**
     * Financial Impact
     */
    @Column(name = "estimated_loss", precision = 19, scale = 4)
    private BigDecimal estimatedLoss;

    @Column(name = "recovered_amount", precision = 19, scale = 4)
    private BigDecimal recoveredAmount;

    /**
     * Chargeback Information
     */
    @Column(name = "chargeback_filed")
    @Builder.Default
    private Boolean chargebackFiled = false;

    @Column(name = "chargeback_date")
    private LocalDateTime chargebackDate;

    /**
     * Audit Fields
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /**
     * Case Status Enum
     */
    public enum CaseStatus {
        OPEN,
        UNDER_INVESTIGATION,
        PENDING_REVIEW,
        RESOLVED,
        CLOSED,
        ESCALATED,
        ARCHIVED
    }

    /**
     * Fraud Case Decision Enum
     */
    public enum FraudCaseDecision {
        APPROVED,
        REJECTED,
        BLOCKED,
        FLAGGED_FOR_REVIEW,
        REQUIRES_VERIFICATION,
        CLEARED
    }

    /**
     * Check if case is still open
     */
    public boolean isOpen() {
        return status == CaseStatus.OPEN ||
               status == CaseStatus.UNDER_INVESTIGATION ||
               status == CaseStatus.PENDING_REVIEW;
    }

    /**
     * Check if case is closed
     */
    public boolean isClosed() {
        return status == CaseStatus.CLOSED ||
               status == CaseStatus.ARCHIVED;
    }

    /**
     * Check if confirmed as fraud
     */
    public boolean isConfirmedFraud() {
        return Boolean.TRUE.equals(confirmedFraud);
    }

    /**
     * Check if false positive
     */
    public boolean isFalsePositive() {
        return Boolean.TRUE.equals(falsePositive);
    }

    /**
     * Check if high risk
     */
    public boolean isHighRisk() {
        return finalRiskScore != null && finalRiskScore > 0.7;
    }

    /**
     * Close the case
     */
    public void close(String resolution) {
        this.status = CaseStatus.CLOSED;
        this.closedAt = LocalDateTime.now();
        this.resolutionNotes = resolution;
    }

    /**
     * Mark as confirmed fraud
     */
    public void confirmFraud(String fraudType, BigDecimal loss) {
        this.confirmedFraud = true;
        this.fraudType = fraudType;
        this.estimatedLoss = loss;
        this.falsePositive = false;
    }

    /**
     * Mark as false positive
     */
    public void markAsFalsePositive(String reason) {
        this.falsePositive = true;
        this.falsePositiveReason = reason;
        this.confirmedFraud = false;
    }

    /**
     * Add evidence to case
     */
    public void addEvidence(String key, String value) {
        if (this.evidence == null) {
            this.evidence = new HashMap<>();
        }
        this.evidence.put(key, value);
    }

    /**
     * Add ML feature
     */
    public void addMlFeature(String featureName, Double value) {
        if (this.mlFeatures == null) {
            this.mlFeatures = new HashMap<>();
        }
        this.mlFeatures.put(featureName, value);
    }

    /**
     * Add triggered rule
     */
    public void addTriggeredRule(String ruleId) {
        if (this.triggeredRules == null) {
            this.triggeredRules = new ArrayList<>();
        }
        if (!this.triggeredRules.contains(ruleId)) {
            this.triggeredRules.add(ruleId);
            this.triggeredRulesCount = this.triggeredRules.size();
        }
    }
}
