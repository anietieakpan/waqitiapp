package com.waqiti.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Spending Pattern DTO
 *
 * Identified spending pattern with characteristics and predictions.
 *
 * @author Waqiti Analytics Team
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpendingPattern {

    private String patternType; // RECURRING, SEASONAL, IMPULSE, PLANNED
    private String category;
    private BigDecimal averageAmount;
    private String frequency;

    private List<String> triggerFactors; // PAYDAY, WEEKEND, HOLIDAY, etc.
    private BigDecimal predictability; // 0.0 - 1.0

    private String recommendation;
}
