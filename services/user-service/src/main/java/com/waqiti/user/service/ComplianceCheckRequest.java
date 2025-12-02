package com.waqiti.user.service;

import java.util.List;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class ComplianceCheckRequest {
    private String userId;
    private String checkType;
    private List<String> checkCategories;
    private String riskLevel;
    private java.time.Instant requestTimestamp;
}
