package com.waqiti.user.service;

import java.time.LocalDateTime;
import java.util.List;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class ComplianceCheckResult {
    private boolean compliant;
    private double complianceScore;
    private String riskLevel;
    private List<String> flaggedCategories;
    private List<String> recommendedActions;
    private String provider;
    private boolean requiresAction;
    private LocalDateTime nextReviewDate;
}
