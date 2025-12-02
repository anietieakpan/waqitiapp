package com.waqiti.insurance.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "risk_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "overall_risk_score", precision = 5, scale = 2)
    private BigDecimal overallRiskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_category", length = 20)
    private InsurancePolicy.RiskCategory riskCategory;

    @Column(name = "claims_history_count")
    private Integer claimsHistoryCount = 0;

    @Column(name = "total_claims_value", precision = 19, scale = 2)
    private BigDecimal totalClaimsValue = BigDecimal.ZERO;

    @Column(name = "fraud_indicators", columnDefinition = "TEXT")
    private String fraudIndicators;

    @Column(name = "credit_score")
    private Integer creditScore;

    @Column(name = "occupation_risk_level")
    private String occupationRiskLevel;

    @Column(name = "health_risk_factors", columnDefinition = "TEXT")
    private String healthRiskFactors;

    @Column(name = "lifestyle_factors", columnDefinition = "TEXT")
    private String lifestyleFactors;

    @Column(name = "assessment_date", nullable = false)
    private LocalDateTime assessmentDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
