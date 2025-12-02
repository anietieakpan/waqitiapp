package com.waqiti.analytics.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Behavior insights model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorInsights {
    private String spendingPersonality;
    private List<String> behaviorTrends;
    private Map<String, Object> riskIndicators;
    private List<String> recommendations;
}