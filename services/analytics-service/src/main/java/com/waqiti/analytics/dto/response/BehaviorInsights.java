package com.waqiti.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Behavior Insights DTO
 *
 * Psychological and behavioral insights derived from transaction patterns.
 *
 * @author Waqiti Analytics Team
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorInsights {

    private String spendingPersonality; // SAVER, SPENDER, BALANCED
    private String riskTolerance; // CONSERVATIVE, MODERATE, AGGRESSIVE

    private List<String> spendingTriggers;
    private List<String> savingOpportunities;

    private Map<String, String> psychologicalPatterns;
    private List<String> behavioralRecommendations;

    private Integer selfControlScore; // 0-100
    private Integer financialDisciplineScore; // 0-100
}
