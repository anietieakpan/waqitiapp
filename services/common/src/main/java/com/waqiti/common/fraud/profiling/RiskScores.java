package com.waqiti.common.fraud.profiling;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime; /**
 * Comprehensive risk scores
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskScores {
    private double behavioralRisk;
    private double transactionalRisk;
    private double externalRisk;
    private double overallRisk;
    private UserRiskProfileService.RiskLevel riskLevel;
    private LocalDateTime calculatedAt;
}
