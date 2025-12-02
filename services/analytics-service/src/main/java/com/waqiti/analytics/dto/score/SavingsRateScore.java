package com.waqiti.analytics.dto.score;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavingsRateScore {
    private BigDecimal score; // 0-100
    private BigDecimal savingsRate; // Percentage of income saved
    private String level; // EXCELLENT, GOOD, FAIR, POOR
    private BigDecimal emergencyFundRatio;
    private String recommendation;
}