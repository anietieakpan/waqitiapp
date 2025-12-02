package com.waqiti.analytics.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Category behavior model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryBehavior {
    private String category;
    private String behaviorType;
    private BigDecimal frequency;
    private BigDecimal averageAmount;
    private String timePattern;
}