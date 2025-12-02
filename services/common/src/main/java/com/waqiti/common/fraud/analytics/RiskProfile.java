package com.waqiti.common.fraud.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map; /**
 * Risk profile
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskProfile {
    private double riskScore;
    private String riskCategory;
    private List<String> riskFactors;
    private Map<String, Object> profileData;
}
