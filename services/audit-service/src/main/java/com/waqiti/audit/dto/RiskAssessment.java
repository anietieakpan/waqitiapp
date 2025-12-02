package com.waqiti.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for Risk Assessment for Basel III compliance
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAssessment {
    private String assessmentId;
    private String assessmentType;
    private LocalDateTime assessmentDate;
    private String assessedBy;
    private String reviewedBy;
    private String status;
    
    // Basel III specific metrics
    private BigDecimal capitalAdequacyRatio;
    private BigDecimal tier1CapitalRatio;
    private BigDecimal commonEquityTier1Ratio;
    private BigDecimal leverageRatio;
    private BigDecimal liquidityCoverageRatio;
    private BigDecimal netStableFundingRatio;
    
    // Risk categories
    private CreditRisk creditRisk;
    private MarketRisk marketRisk;
    private OperationalRisk operationalRisk;
    private LiquidityRisk liquidityRisk;
    private ConcentrationRisk concentrationRisk;
    
    // Risk metrics
    private BigDecimal totalRiskWeightedAssets;
    private BigDecimal expectedLoss;
    private BigDecimal unexpectedLoss;
    private BigDecimal valueAtRisk;
    private BigDecimal conditionalValueAtRisk;
    private BigDecimal economicCapital;
    
    // Stress testing
    private List<StressScenario> stressScenarios;
    private Map<String, BigDecimal> stressTestResults;
    
    // Compliance assessment
    private Boolean meetsMinimumRequirements;
    private List<String> breaches;
    private List<String> recommendations;
    private String actionPlan;
    private LocalDateTime nextReviewDate;
}

