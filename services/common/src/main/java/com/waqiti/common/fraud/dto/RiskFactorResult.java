package com.waqiti.common.fraud.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class RiskFactorResult {
    private long accountAge;
    private AmountRiskLevel amountRiskLevel;
    private double velocityScore;
    private GeographicRisk geographicRisk;
    private DeviceRisk deviceRisk;
    private TimeRisk timeRisk;
    private double confidence;
    private List<String> riskFactors;
}