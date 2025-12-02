package com.waqiti.payment.fraud.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Fraud Review Case Entity
 *
 * Represents a transaction flagged for manual fraud review by a human analyst.
 * Includes all context needed for fraud analysts to make informed decisions.
 *
 * @author Waqiti Fraud Detection Team
 * @version 2.0.0
 */
@Entity
@Table(name = "fraud_review_cases", indexes = {
    @Index(name = "idx_fraud_review_status_priority", columnList = "status, priority, queued_at"),
    @Index(name = "idx_fraud_review_payment_id", columnList = "payment_id"),
    @Index(name = "idx_fraud_review_user_id", columnList = "user_id"),
    @Index(name = "idx_fraud_review_assigned_analyst", columnList = "assigned_analyst"),
    @Index(name = "idx_fraud_review_sla_deadline", columnList = "sla_deadline"),
    @Index(name = "idx_fraud_review_id", columnList = "review_id", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudReviewCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "review_id", nullable = false, unique = true, length = 50)
    private String reviewId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "risk_score", nullable = false)
    private Double riskScore;

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @ElementCollection
    @CollectionTable(name = "fraud_review_triggered_rules",
        joinColumns = @JoinColumn(name = "fraud_review_case_id"))
    @Column(name = "rule_name")
    private List<String> rulesTriggered;

    @Column(name = "priority", nullable = false)
    private Integer priority; // 0=Critical, 1=High, 2=Medium, 3=Low

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FraudReviewStatus status;

    @Column(name = "device_id", length = 255)
    private String deviceId;

    @Column(name = "request_id", length = 100)
    private String requestId;

    // Queue timestamps
    @Column(name = "queued_at", nullable = false)
    private LocalDateTime queuedAt;

    @Column(name = "sla_deadline", nullable = false)
    private LocalDateTime slaDeadline;

    // Review process
    @Column(name = "assigned_analyst", length = 100)
    private String assignedAnalyst;

    @Column(name = "review_started_at")
    private LocalDateTime reviewStartedAt;

    @Column(name = "review_completed_at")
    private LocalDateTime reviewCompletedAt;

    @Column(name = "review_duration_minutes")
    private Long reviewDurationMinutes;

    // Decision
    @Enumerated(EnumType.STRING)
    @Column(name = "decision", length = 20)
    private FraudReviewDecision decision;

    @Column(name = "decision_notes", columnDefinition = "TEXT")
    private String decisionNotes;

    @Column(name = "reviewer_id", length = 100)
    private String reviewerId; // Analyst who made the decision

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason; // Specific reason if rejected

    @Column(name = "sla_violation")
    private boolean slaViolation;

    // Escalation
    @Column(name = "escalated")
    private boolean escalated;

    @Column(name = "escalation_reason", columnDefinition = "TEXT")
    private String escalationReason;

    @Column(name = "escalated_by", length = 100)
    private String escalatedBy;

    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    // Audit timestamps
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version;
}
