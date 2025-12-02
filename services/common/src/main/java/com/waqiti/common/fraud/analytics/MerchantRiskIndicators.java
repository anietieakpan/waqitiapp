package com.waqiti.common.fraud.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map; /**
 * Merchant risk indicators
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantRiskIndicators {
    private double riskScore;
    private boolean isHighRisk;
    private List<String> riskFactors;
    private Map<String, Double> riskMetrics;
}
