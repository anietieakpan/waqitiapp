package com.waqiti.analytics.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Personalized recommendation model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonalizedRecommendation {
    private String recommendationType; // SAVINGS, SPENDING, INVESTMENT, BUDGET
    private String title;
    private String description;
    private String actionItem;
    private BigDecimal potentialSavings;
    private String priority; // HIGH, MEDIUM, LOW
    private String category;
    private LocalDateTime generatedAt;
    private String reasoning;
}