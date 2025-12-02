package com.waqiti.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Compliance Report DTO for regulatory and compliance reporting
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceReport {
    
    private String reportId;
    private String reportType;
    private LocalDateTime generatedAt;
    private String generatedBy;
    
    // Compliance Overview
    private String overallStatus; // COMPLIANT, PARTIALLY_COMPLIANT, NON_COMPLIANT
    private Integer complianceScore; // 0-100
    private LocalDate lastAuditDate;
    private LocalDate nextAuditDate;
    
    // Compliance Details
    private List<ComplianceItem> complianceItems;
    private List<ComplianceViolation> violations;
    private List<ComplianceRecommendation> recommendations;
    
    // Summary Statistics
    private ComplianceSummary summary;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceItem {
        private String itemId;
        private String requirement;
        private String category; // AML, KYC, GDPR, PCI-DSS, etc.
        private String status; // COMPLIANT, PARTIALLY_COMPLIANT, NON_COMPLIANT
        private LocalDate lastChecked;
        private String checkedBy;
        private String notes;
        private List<String> evidenceLinks;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceViolation {
        private String violationId;
        private String type;
        private String severity; // LOW, MEDIUM, HIGH, CRITICAL
        private String description;
        private LocalDateTime detectedAt;
        private String detectedBy;
        private String status; // OPEN, IN_PROGRESS, RESOLVED
        private LocalDateTime resolvedAt;
        private String resolution;
        private List<String> affectedEntities;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceRecommendation {
        private String recommendationId;
        private String category;
        private String priority; // LOW, MEDIUM, HIGH
        private String recommendation;
        private String impact;
        private String effort; // LOW, MEDIUM, HIGH
        private LocalDate targetDate;
        private String assignedTo;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceSummary {
        private Integer totalRequirements;
        private Integer compliantItems;
        private Integer partiallyCompliantItems;
        private Integer nonCompliantItems;
        private Integer openViolations;
        private Integer resolvedViolations;
        private Integer pendingRecommendations;
        private Double complianceTrend; // Percentage change from last period
    }
}