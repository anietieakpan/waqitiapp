package com.waqiti.account.service;

import java.util.Map;
import java.util.UUID;

@lombok.Data
@lombok.Builder
public class RiskAssessmentRequest {
    private UUID userId;
    private String assessmentType;
    private Map<String, Object> factors;
    private Long timestamp;
}
