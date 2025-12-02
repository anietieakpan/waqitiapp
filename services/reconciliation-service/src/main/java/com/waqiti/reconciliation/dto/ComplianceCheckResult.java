package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceCheckResult {

    private UUID accountId;
    
    private boolean compliant;
    
    @Builder.Default
    private LocalDateTime checkedAt = LocalDateTime.now();
    
    private List<ComplianceViolation> violations;
    
    private List<ComplianceWarning> warnings;
    
    private ComplianceScore overallScore;
    
    private String checkedBy;
    
    private String complianceFramework;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceViolation {
        private String violationType;
        private String description;
        private String severity;
        private String regulatoryReference;
        private String remedialAction;
        private boolean requiresReporting;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceWarning {
        private String warningType;
        private String description;
        private String recommendation;
        private LocalDateTime detectedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceScore {
        private int score;
        private String grade;
        private String riskLevel;
        private String assessment;
    }

    public boolean isCompliant() {
        return compliant && (violations == null || violations.isEmpty());
    }

    public boolean hasViolations() {
        return violations != null && !violations.isEmpty();
    }

    public boolean hasCriticalViolations() {
        return violations != null && 
               violations.stream().anyMatch(v -> "CRITICAL".equalsIgnoreCase(v.getSeverity()));
    }

    public boolean requiresReporting() {
        return violations != null && 
               violations.stream().anyMatch(ComplianceViolation::isRequiresReporting);
    }
}