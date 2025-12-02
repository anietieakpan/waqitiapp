package com.waqiti.common.fraud.location;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor; /**
 * Country risk check result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CountryRiskCheck {
    private String countryCode;
    private LocationService.RiskLevel riskLevel;
    private double riskScore;
    private boolean isSanctioned;
    private boolean hasTradeRestrictions;
}
