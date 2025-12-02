package com.waqiti.analytics.dto.recommendation;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetRecommendation {
    private BigDecimal recommendedBudget;
    private Map<String, BigDecimal> categoryBudgets;
    private String budgetingStrategy; // 50_30_20, ZERO_BASED, ENVELOPE
    private BigDecimal emergencyFundTarget;
    private String adjustmentReason;
}