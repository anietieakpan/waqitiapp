package com.waqiti.audit.client;

import java.util.List;
import java.util.Map;

public record ThreatIntelligenceResponse(
    String indicator,
    String threatLevel,
    String threatType,
    List<String> associatedThreats,
    Map<String, Object> intelligence
) {}
