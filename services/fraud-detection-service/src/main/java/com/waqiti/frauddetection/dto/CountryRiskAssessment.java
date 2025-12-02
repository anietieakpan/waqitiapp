package com.waqiti.frauddetection.dto;

import com.waqiti.frauddetection.domain.RiskLevel;
import lombok.Builder;
import lombok.Value;

/**
 * Comprehensive country risk assessment result
 * Used for detailed fraud analysis and compliance reporting
 */
@Value
@Builder
public class CountryRiskAssessment {
    String countryCode;
    RiskLevel riskLevel;
    double riskScore;
    boolean isSanctioned;
    boolean requiresEDD;
    String reason;
    String sanctionStatus;
}