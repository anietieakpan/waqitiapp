package com.waqiti.user.service;

import java.math.BigDecimal;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class RiskProfile {
    private String userId;
    private BigDecimal currentScore;
    private String riskLevel;
}
