package com.waqiti.user.service;

import java.util.List;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class EnhancedDueDiligenceRequest {
    private String userId;
    private String dueDiligenceLevel;
    private List<String> riskFactors;
    private String sourceOfFunds;
    private String businessPurpose;
    private java.time.Instant requestTimestamp;
}
