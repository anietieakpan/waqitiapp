package com.waqiti.audit.client;

import java.util.List;
import java.util.Map;

public record RiskAnalysisResponse(
    double riskScore,
    String riskLevel,
    List<String> riskFactors,
    Map<String, Object> recommendations
) {}
