package com.waqiti.payment.service;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ComplianceReport {
    private String reportId;
    private String businessId;
    private String reportType;
    private int complianceScore;
    private String riskAssessment;
    private List<String> violations;
    private List<String> recommendations;
    private Map<String, Object> details;
    private LocalDate nextReviewDate;
    private Instant generatedAt;
}