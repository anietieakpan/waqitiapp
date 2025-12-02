package com.waqiti.analytics.dto.recommendation;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonalizedRecommendation {
    private String id;
    private String type; // SPENDING, SAVINGS, INVESTMENT, SECURITY, BUDGETING
    private String title;
    private String description;
    private String priority; // HIGH, MEDIUM, LOW
    private String category;
    private BigDecimal potentialSavings;
    private String timeframe;
    private String actionRequired;
}