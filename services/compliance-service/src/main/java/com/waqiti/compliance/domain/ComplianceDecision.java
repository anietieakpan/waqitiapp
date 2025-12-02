package com.waqiti.compliance.domain;

import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Compliance Decision Entity
 * Records decisions made during compliance checks
 */
@Entity
@Table(name = "compliance_decisions", indexes = {
    @Index(name = "idx_decision_transaction", columnList = "transaction_id"),
    @Index(name = "idx_decision_type", columnList = "decision_type"),
    @Index(name = "idx_decision_status", columnList = "decision"),
    @Index(name = "idx_decision_risk", columnList = "risk_score"),
    @Index(name = "idx_decision_created", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"decisionMetadata"})
@EqualsAndHashCode(of = {"id"})
public class ComplianceDecision {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    
    @Column(name = "transaction_id", nullable = false)
    private String transactionId;
    
    @Column(name = "customer_id")
    private String customerId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "decision_type", nullable = false)
    private DecisionType decisionType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false)
    private Decision decision;
    
    @Column(name = "risk_score")
    private Integer riskScore;
    
    @Column(name = "confidence_score")
    private Double confidenceScore;
    
    @Column(name = "reason", length = 1000)
    private String reason;
    
    @Type(type = "jsonb")
    @Column(name = "decision_metadata", columnDefinition = "jsonb")
    private Map<String, Object> decisionMetadata;
    
    @Column(name = "created_by")
    private String createdBy;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @Column(name = "reviewed_by")
    private String reviewedBy;
    
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
    
    public enum DecisionType {
        TRANSACTION_SCREENING,
        CUSTOMER_ONBOARDING,
        KYC_VERIFICATION,
        AML_MONITORING,
        SANCTIONS_SCREENING,
        PEP_SCREENING,
        TRANSACTION_MONITORING,
        MANUAL_REVIEW
    }
    
    public enum Decision {
        APPROVED,
        REJECTED,
        PENDING,
        MANUAL_REVIEW,
        SUSPICIOUS,
        BLOCKED,
        CONDITIONAL_APPROVAL
    }
    
    /**
     * Check if decision is final
     */
    public boolean isFinal() {
        return decision == Decision.APPROVED || 
               decision == Decision.REJECTED || 
               decision == Decision.BLOCKED;
    }
    
    /**
     * Check if decision requires manual review
     */
    public boolean requiresManualReview() {
        return decision == Decision.MANUAL_REVIEW || 
               decision == Decision.SUSPICIOUS;
    }
    
    /**
     * Check if decision is expired
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}