package com.waqiti.insurance.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "policy_underwritings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyUnderwriting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "policy_id", nullable = false)
    private InsurancePolicy policy;

    @Column(name = "underwriter_id", nullable = false)
    private UUID underwriterId;

    @Column(name = "risk_score", precision = 5, scale = 2)
    private BigDecimal riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "underwriting_decision", length = 30)
    private UnderwritingDecision decision;

    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;

    @Column(name = "recommended_premium", precision = 19, scale = 2)
    private BigDecimal recommendedPremium;

    @Column(name = "conditions", columnDefinition = "TEXT")
    private String conditions;

    @Column(name = "exclusions", columnDefinition = "TEXT")
    private String exclusions;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum UnderwritingDecision {
        APPROVED, APPROVED_WITH_CONDITIONS, DECLINED, PENDING_REVIEW, REQUIRES_MEDICAL_EXAM
    }
}
