package com.waqiti.analytics.api;

import java.util.Map;

@lombok.Data
@lombok.Builder
public class ScenarioRequest {
    private String scenarioName;
    private Map<String, Object> variables;
    private Map<String, Object> assumptions;
}
