package com.waqiti.analytics.dto.pattern;

import com.waqiti.analytics.dto.model.SpendingPattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Arrays;

/**
 * Recurring Pattern DTO for subscription and recurring payment detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringPattern {
    private String merchant;
    private BigDecimal amount;
    private String frequency; // WEEKLY, BIWEEKLY, MONTHLY
    private BigDecimal confidence;
    private String description;

    /**
     * Convert recurring pattern to spending pattern
     */
    public SpendingPattern toSpendingPattern() {
        return SpendingPattern.builder()
                .patternName("Recurring: " + merchant)
                .description("Recurring payment to " + merchant + " - " + frequency)
                .frequency(confidence)
                .averageAmount(amount)
                .categories(Arrays.asList("SUBSCRIPTION", "RECURRING"))
                .timePattern(frequency)
                .confidence(confidence)
                .build();
    }
}
