package com.waqiti.analytics.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Behavioral patterns model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehavioralPatterns {
    private String spendingPersonality; // CONSERVATIVE, MODERATE, AGGRESSIVE
    private List<String> habitualPurchases;
    private Map<String, String> dayOfWeekPatterns;
    private Map<String, String> timeOfDayPatterns;
    private List<String> unusualBehaviors;
    private BigDecimal consistencyScore;
    private List<CategoryBehavior> categoryBehaviors;
    private String riskProfile;
    private BigDecimal impulsivityScore;
}