package com.waqiti.common.fraud.profiling;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map; /**
 * External risk data from third-party sources
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalRiskData {
    private String userId;
    private double creditRisk;
    private double identityRisk;
    private double reputationRisk;
    private LocalDateTime dataDate;
    private Map<String, Object> externalScores;
    private Map<String, Object> riskIndicators;
    private Integer creditScore;
    private Boolean hasAdverseMedia;
    private Boolean isPoliticallyExposed;
    private Boolean sanctionsMatch;

    public double calculateRiskScore() {
        return (creditRisk + identityRisk + reputationRisk) / 3.0;
    }
}
