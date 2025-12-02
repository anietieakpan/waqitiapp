package com.waqiti.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StressScenario {
    private String scenarioName;
    private String description;
    private Map<String, Double> parameters;
    private BigDecimal impactOnCapital;
    private BigDecimal impactOnLiquidity;
}
