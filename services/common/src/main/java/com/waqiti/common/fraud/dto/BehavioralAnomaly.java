package com.waqiti.common.fraud.dto;

import lombok.Builder;
import lombok.Data;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
public class BehavioralAnomaly {
    private AnomalyType type;
    private AnomalySeverity severity;
    private String description;
    private double confidence;

    @Builder.Default
    private Map<String, Object> details = new HashMap<>();
}