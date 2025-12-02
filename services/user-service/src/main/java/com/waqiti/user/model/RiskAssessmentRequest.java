package com.waqiti.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAssessmentRequest {
    private String userId;
    private String assessmentType;
    private List<String> riskFactors;
    private String assessmentLevel;
    private Instant requestTimestamp;
}
