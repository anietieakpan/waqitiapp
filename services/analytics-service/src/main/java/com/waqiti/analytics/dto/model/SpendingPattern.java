package com.waqiti.analytics.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Spending pattern model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpendingPattern {
    private String patternName;
    private String description;
    private BigDecimal frequency;
    private BigDecimal averageAmount;
    private List<String> categories;
    private String timePattern;
}