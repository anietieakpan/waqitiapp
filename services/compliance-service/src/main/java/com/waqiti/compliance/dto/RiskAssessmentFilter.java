package com.waqiti.compliance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAssessmentFilter {
    private String entityType;
    private String riskLevel;
    private LocalDate startDate;
    private LocalDate endDate;
    private String assessedBy;
    private Boolean reviewRequired;
}