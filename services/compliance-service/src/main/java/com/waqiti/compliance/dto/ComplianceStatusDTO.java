package com.waqiti.compliance.dto;

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
public class ComplianceStatusDTO {
    
    private String userId;
    private String transactionId;
    private ComplianceLevel complianceLevel;
    private ComplianceStatus status;
    private List<ComplianceCheck> checks;
    private Map<String, Object> details;
    private List<String> actions;
    private List<String> trends;
    private List<String> recipients;
    private List<String> metadata;
    private List<String> parameters;
    private List<String> submissionDetails;
    private List<String> validationDetails;
    private List<String> reportsByType;
    private List<String> healthChecks;
    private List<String> mitigationActions;
    private List<String> upcomingDeadlines;
    private List<String> dataPoints;
    private List<String> supportingDocuments;
    private List<String> errors;
    private List<String> warnings;
    private List<String> metrics;
    private List<String> activeAlerts;
    private List<String> submissionParameters;
    private List<String> riskFactors;
    private LocalDateTime timestamp;
    private LocalDateTime lastUpdated;
    
    public enum ComplianceLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    public enum ComplianceStatus {
        PENDING, IN_REVIEW, APPROVED, REJECTED, ESCALATED
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceCheck {
        private String checkType;
        private String checkName;
        private boolean passed;
        private String reason;
        private LocalDateTime performedAt;
        private Map<String, Object> additionalInfo;
    }
}