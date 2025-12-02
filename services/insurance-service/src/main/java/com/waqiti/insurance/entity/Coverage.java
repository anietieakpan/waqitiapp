package com.waqiti.insurance.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "coverages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Coverage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private InsurancePolicy policy;

    @Column(name = "coverage_type", nullable = false, length = 50)
    private String coverageType;

    @Column(name = "coverage_limit", precision = 19, scale = 2)
    private BigDecimal coverageLimit;

    @Column(name = "deductible", precision = 19, scale = 2)
    private BigDecimal deductible;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "active")
    private Boolean active = true;
}
