package com.waqiti.analytics.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Financial health score model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialHealthScore {
    private BigDecimal overallScore; // 0.0 to 100.0
    private String healthLevel; // POOR, FAIR, GOOD, EXCELLENT
    private Map<String, BigDecimal> scoreComponents;
    private BigDecimal savingsRate;
    private BigDecimal debtToIncomeRatio;
    private BigDecimal emergencyFundMonths;
    private BigDecimal creditUtilization;
    private String trend; // IMPROVING, STABLE, DECLINING
    private LocalDateTime calculatedAt;
    private Map<String, String> recommendations;
}