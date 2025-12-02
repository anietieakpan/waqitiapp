package com.waqiti.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRiskProfile {
    private String userId;
    private BigDecimal currentScore;
    private String riskLevel;
    private List<String> activeRiskFactors;
}
