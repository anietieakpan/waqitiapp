package com.waqiti.common.observability.dto;

import com.waqiti.common.enums.RiskLevel;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class RiskAnalysis {
    private RiskLevel riskLevel;
    private double riskScore;
    private List<String> riskFactors;
    private List<String> mitigations;
    private double probabilityOfBreach;
    private String riskSummary;
}