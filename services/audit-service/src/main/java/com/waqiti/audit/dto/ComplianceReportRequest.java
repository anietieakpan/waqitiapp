package com.waqiti.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Request DTO for generating compliance reports
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceReportRequest {
    private String reportName;
    private String reportType;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    
    // Filtering criteria
    private List<String> eventTypes;
    private List<String> entityTypes;
    private List<String> userIds;
    private List<String> services;
    private List<String> riskLevels;
    private List<String> complianceFrameworks;
    
    // Report configuration
    private Boolean includeExecutiveSummary;
    private Boolean includeDetailedFindings;
    private Boolean includeRecommendations;
    private Boolean includeMetrics;
    private Boolean includeTrends;
    private Boolean includeEvidence;
    
    // Output preferences
    private String outputFormat; // PDF, EXCEL, JSON, XML
    private String language; // EN, ES, FR, etc.
    private String timezone;
    
    // Custom parameters
    private Map<String, Object> customParameters;
    private List<String> customMetrics;
    private Map<String, String> thresholds;
    
    // Scheduling (for recurring reports)
    private Boolean isRecurring;
    private String recurrencePattern; // DAILY, WEEKLY, MONTHLY, QUARTERLY
    private LocalDateTime nextScheduledRun;
    private String recipientEmails;
    
    // Compliance specific options
    private Boolean includeRemediationTracking;
    private Boolean includeRiskAssessment;
    private Boolean includeControlEffectiveness;
    private Boolean includeAuditTrail;
}