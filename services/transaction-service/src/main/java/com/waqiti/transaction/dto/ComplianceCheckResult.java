package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ComplianceCheckResult {
    private boolean compliant;
    private String violationReason;
    private double riskScore;
}