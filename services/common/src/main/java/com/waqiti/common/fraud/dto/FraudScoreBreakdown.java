package com.waqiti.common.fraud.dto;

import lombok.Builder;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class FraudScoreBreakdown {
    private double mlModelScore;
    private double mlModelConfidence;
    private double ruleBasedScore;
    private double anomalyScore;
    private double patternScore;
    private double finalScore;

    @Builder.Default
    private List<ScoringComponent> scoringComponents = new ArrayList<>();
}