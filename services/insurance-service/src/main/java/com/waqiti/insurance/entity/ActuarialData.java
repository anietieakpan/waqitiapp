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
@Table(name = "actuarial_data")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActuarialData {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "policy_type", nullable = false, length = 30)
    private String policyType;

    @Column(name = "age_group", nullable = false, length = 20)
    private String ageGroup;

    @Column(name = "risk_category", nullable = false, length = 20)
    private String riskCategory;

    @Column(name = "mortality_rate", precision = 10, scale = 8)
    private BigDecimal mortalityRate;

    @Column(name = "morbidity_rate", precision = 10, scale = 8)
    private BigDecimal morbidityRate;

    @Column(name = "claim_frequency", precision = 10, scale = 8)
    private BigDecimal claimFrequency;

    @Column(name = "average_claim_amount", precision = 19, scale = 2)
    private BigDecimal averageClaimAmount;

    @Column(name = "loss_ratio", precision = 5, scale = 4)
    private BigDecimal lossRatio;

    @Column(name = "base_premium_rate", precision = 10, scale = 6)
    private BigDecimal basePremiumRate;

    @Column(name = "risk_adjustment_factor", precision = 5, scale = 4)
    private BigDecimal riskAdjustmentFactor;

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
