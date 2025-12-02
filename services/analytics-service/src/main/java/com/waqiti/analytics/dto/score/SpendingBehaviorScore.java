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
public class SpendingBehaviorScore {
    private BigDecimal score; // 0-100
    private String level; // EXCELLENT, GOOD, FAIR, POOR
    private BigDecimal consistency;
    private BigDecimal budgetAdherence;
    private BigDecimal categoryDiversification;
    private String primaryWeakness;
}