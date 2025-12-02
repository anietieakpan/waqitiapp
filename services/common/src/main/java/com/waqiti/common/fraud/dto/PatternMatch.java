package com.waqiti.common.fraud.dto;

import lombok.Builder;
import lombok.Data;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
public class PatternMatch {
    private FraudPatternType patternType;
    private double confidence;
    private String description;

    @Builder.Default
    private Map<String, Object> matchDetails = new HashMap<>();
}