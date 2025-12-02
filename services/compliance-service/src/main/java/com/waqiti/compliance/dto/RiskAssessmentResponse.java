package com.waqiti.compliance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAssessmentResponse {
    private UUID assessmentId;
    private String entityId;
    private String entityType;
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private Double riskScore;
    private List<String> riskFactors;
    private List<String> mitigationMeasures;
    private String recommendation; // APPROVE, REJECT, ENHANCED_MONITORING
    private LocalDateTime assessedAt;
    private String assessedBy;
    private LocalDateTime nextAssessmentDue;
    private Map<String, Object> riskBreakdown;
}