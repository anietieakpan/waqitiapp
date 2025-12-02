package com.waqiti.gdpr.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetentionComplianceReport {
    private String reportId;
    private String period;
    private Map<String, Object> personalDataProcessed;
    private Map<String, Object> transactionDataProcessed;
    private Map<String, Object> kycDataProcessed;
    private Map<String, Object> consentDataProcessed;
    private Map<String, Object> sessionDataProcessed;
    private double complianceRate;
    private double dataMinimizationScore;
    private double storageLimitationScore;
    private List<String> issuesIdentified;
    private List<String> recommendations;
    private LocalDateTime generatedAt;
}