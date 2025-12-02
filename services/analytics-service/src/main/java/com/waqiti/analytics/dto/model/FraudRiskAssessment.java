package com.waqiti.analytics.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Fraud risk assessment model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudRiskAssessment {
    private BigDecimal riskScore; // 0.0 to 100.0
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private List<RiskIndicator> riskIndicators;
    private List<SuspiciousActivity> suspiciousActivities;
    private BigDecimal confidenceLevel;
    private LocalDateTime lastAssessmentDate;
    private List<String> recommendations;
    private Boolean requiresReview;
}