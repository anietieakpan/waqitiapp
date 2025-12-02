package com.waqiti.investment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * DTO for portfolio risk metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioRiskMetricsDto {
    private Long accountId;
    private BigDecimal valueAtRisk95; // 95% confidence VaR
    private BigDecimal valueAtRisk99; // 99% confidence VaR
    private BigDecimal maxDrawdown;
    private BigDecimal concentrationRisk;
    private Map<String, Map<String, BigDecimal>> correlationMatrix;
    private BigDecimal riskScore; // Overall risk score (0-100)
    private String riskLevel; // LOW, MEDIUM, HIGH
}