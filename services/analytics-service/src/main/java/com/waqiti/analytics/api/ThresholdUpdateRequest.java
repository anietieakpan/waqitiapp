package com.waqiti.analytics.api;

import java.util.Map;

@lombok.Data
@lombok.Builder
public class ThresholdUpdateRequest {
    private Map<String, Double> thresholds;
}
