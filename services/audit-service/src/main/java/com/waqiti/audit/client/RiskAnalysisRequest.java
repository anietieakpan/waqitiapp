package com.waqiti.audit.client;

import java.util.Map;

public record RiskAnalysisRequest(
    String userId,
    String activityType,
    String resource,
    Map<String, Object> context
) {}
