package com.waqiti.common.audit;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive audit summary for PCI DSS and regulatory compliance reporting
 * Contains aggregated audit data for various time periods and compliance checks
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditSummary {

    @JsonProperty("summary_id")
    private String summaryId;

    @JsonProperty("report_period")
    private ReportPeriod reportPeriod;

    @JsonProperty("compliance_status")
    private ComplianceStatus complianceStatus;

    @JsonProperty("security_metrics")
    private SecurityMetrics securityMetrics;

    @JsonProperty("transaction_metrics")
    private TransactionMetrics transactionMetrics;

    @JsonProperty("access_metrics")
    private AccessMetrics accessMetrics;

    @JsonProperty("vulnerability_metrics")
    private VulnerabilityMetrics vulnerabilityMetrics;

    @JsonProperty("incident_summary")
    private IncidentSummary incidentSummary;

    @JsonProperty("compliance_violations")
    private List<ComplianceViolation> complianceViolations;

    @JsonProperty("risk_assessment")
    private RiskAssessment riskAssessment;

    @JsonProperty("recommendations")
    private List<String> recommendations;

    @JsonProperty("generated_by")
    private String generatedBy;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("generated_at")
    private LocalDateTime generatedAt;

    @JsonProperty("report_version")
    private String reportVersion;

    @JsonProperty("certification_level")
    private String certificationLevel;

    @JsonProperty("next_audit_date")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime nextAuditDate;

    /**
     * Report period information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportPeriod {
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        @JsonProperty("start_date")
        private LocalDateTime startDate;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        @JsonProperty("end_date")
        private LocalDateTime endDate;

        @JsonProperty("period_type")
        private String periodType; // DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY

        @JsonProperty("total_days")
        private Integer totalDays;

        @JsonProperty("business_days")
        private Integer businessDays;
    }

    /**
     * Overall compliance status
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceStatus {
        @JsonProperty("overall_status")
        private String overallStatus; // COMPLIANT, NON_COMPLIANT, PARTIAL, PENDING_REVIEW

        @JsonProperty("pci_dss_status")
        private String pciDssStatus;

        @JsonProperty("gdpr_status")
        private String gdprStatus;

        @JsonProperty("sox_status")
        private String soxStatus;

        @JsonProperty("iso_27001_status")
        private String iso27001Status;

        @JsonProperty("compliance_score")
        private BigDecimal complianceScore; // 0-100

        @JsonProperty("last_assessment_date")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime lastAssessmentDate;

        @JsonProperty("certification_expiry")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime certificationExpiry;
    }

    /**
     * Security metrics summary
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityMetrics {
        @JsonProperty("total_security_events")
        private Long totalSecurityEvents;

        @JsonProperty("high_severity_events")
        private Long highSeverityEvents;

        @JsonProperty("failed_login_attempts")
        private Long failedLoginAttempts;

        @JsonProperty("blocked_transactions")
        private Long blockedTransactions;

        @JsonProperty("fraud_detections")
        private Long fraudDetections;

        @JsonProperty("suspicious_activities")
        private Long suspiciousActivities;

        @JsonProperty("data_access_violations")
        private Long dataAccessViolations;

        @JsonProperty("encryption_failures")
        private Long encryptionFailures;

        @JsonProperty("security_score")
        private BigDecimal securityScore;

        @JsonProperty("threat_level")
        private String threatLevel; // LOW, MEDIUM, HIGH, CRITICAL
    }

    /**
     * Transaction-related metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionMetrics {
        @JsonProperty("total_transactions")
        private Long totalTransactions;

        @JsonProperty("total_volume")
        private BigDecimal totalVolume;

        @JsonProperty("failed_transactions")
        private Long failedTransactions;

        @JsonProperty("refunded_transactions")
        private Long refundedTransactions;

        @JsonProperty("disputed_transactions")
        private Long disputedTransactions;

        @JsonProperty("average_transaction_value")
        private BigDecimal averageTransactionValue;

        @JsonProperty("pci_compliant_transactions")
        private Long pciCompliantTransactions;

        @JsonProperty("transaction_success_rate")
        private BigDecimal transactionSuccessRate;

        @JsonProperty("processing_time_metrics")
        private ProcessingTimeMetrics processingTimeMetrics;
    }

    /**
     * Access control metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccessMetrics {
        @JsonProperty("total_access_attempts")
        private Long totalAccessAttempts;

        @JsonProperty("successful_logins")
        private Long successfulLogins;

        @JsonProperty("failed_logins")
        private Long failedLogins;

        @JsonProperty("privileged_access_events")
        private Long privilegedAccessEvents;

        @JsonProperty("unauthorized_access_attempts")
        private Long unauthorizedAccessAttempts;

        @JsonProperty("session_timeout_events")
        private Long sessionTimeoutEvents;

        @JsonProperty("password_policy_violations")
        private Long passwordPolicyViolations;

        @JsonProperty("mfa_usage_rate")
        private BigDecimal mfaUsageRate;

        @JsonProperty("average_session_duration")
        private Long averageSessionDuration; // in minutes
    }

    /**
     * Vulnerability assessment metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VulnerabilityMetrics {
        @JsonProperty("total_vulnerabilities")
        private Long totalVulnerabilities;

        @JsonProperty("critical_vulnerabilities")
        private Long criticalVulnerabilities;

        @JsonProperty("high_vulnerabilities")
        private Long highVulnerabilities;

        @JsonProperty("medium_vulnerabilities")
        private Long mediumVulnerabilities;

        @JsonProperty("low_vulnerabilities")
        private Long lowVulnerabilities;

        @JsonProperty("resolved_vulnerabilities")
        private Long resolvedVulnerabilities;

        @JsonProperty("pending_vulnerabilities")
        private Long pendingVulnerabilities;

        @JsonProperty("average_resolution_time")
        private Long averageResolutionTime; // in hours

        @JsonProperty("vulnerability_score")
        private BigDecimal vulnerabilityScore;
    }

    /**
     * Security incident summary
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IncidentSummary {
        @JsonProperty("total_incidents")
        private Long totalIncidents;

        @JsonProperty("security_breaches")
        private Long securityBreaches;

        @JsonProperty("data_leaks")
        private Long dataLeaks;

        @JsonProperty("fraud_incidents")
        private Long fraudIncidents;

        @JsonProperty("system_outages")
        private Long systemOutages;

        @JsonProperty("resolved_incidents")
        private Long resolvedIncidents;

        @JsonProperty("pending_incidents")
        private Long pendingIncidents;

        @JsonProperty("average_resolution_time")
        private Long averageResolutionTime; // in hours

        @JsonProperty("incident_categories")
        private Map<String, Long> incidentCategories;
    }

    /**
     * Compliance violation details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceViolation {
        @JsonProperty("violation_id")
        private String violationId;

        @JsonProperty("violation_type")
        private String violationType;

        @JsonProperty("severity")
        private String severity;

        @JsonProperty("description")
        private String description;

        @JsonProperty("affected_systems")
        private List<String> affectedSystems;

        @JsonProperty("detected_at")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime detectedAt;

        @JsonProperty("resolved_at")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime resolvedAt;

        @JsonProperty("status")
        private String status; // OPEN, IN_PROGRESS, RESOLVED, CLOSED

        @JsonProperty("remediation_actions")
        private List<String> remediationActions;
    }

    /**
     * Risk assessment summary
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskAssessment {
        @JsonProperty("overall_risk_level")
        private String overallRiskLevel; // LOW, MEDIUM, HIGH, CRITICAL

        @JsonProperty("risk_score")
        private BigDecimal riskScore; // 0-100

        @JsonProperty("financial_risk")
        private BigDecimal financialRisk;

        @JsonProperty("operational_risk")
        private BigDecimal operationalRisk;

        @JsonProperty("compliance_risk")
        private BigDecimal complianceRisk;

        @JsonProperty("reputation_risk")
        private BigDecimal reputationRisk;

        @JsonProperty("risk_factors")
        private List<String> riskFactors;

        @JsonProperty("mitigation_strategies")
        private List<String> mitigationStrategies;
    }

    /**
     * Processing time metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessingTimeMetrics {
        @JsonProperty("average_processing_time")
        private Long averageProcessingTime; // in milliseconds

        @JsonProperty("p95_processing_time")
        private Long p95ProcessingTime;

        @JsonProperty("p99_processing_time")
        private Long p99ProcessingTime;

        @JsonProperty("slowest_processing_time")
        private Long slowestProcessingTime;

        @JsonProperty("fastest_processing_time")
        private Long fastestProcessingTime;
    }

    /**
     * Check if the summary indicates compliance
     */
    public boolean isCompliant() {
        return complianceStatus != null && 
               "COMPLIANT".equals(complianceStatus.getOverallStatus());
    }

    /**
     * Check if there are critical security issues
     */
    public boolean hasCriticalIssues() {
        return securityMetrics != null && 
               (securityMetrics.getHighSeverityEvents() > 0 ||
                vulnerabilityMetrics != null && vulnerabilityMetrics.getCriticalVulnerabilities() > 0);
    }

    /**
     * Get overall health score (0-100)
     */
    public BigDecimal getOverallHealthScore() {
        if (complianceStatus == null || securityMetrics == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal complianceWeight = complianceStatus.getComplianceScore().multiply(new BigDecimal("0.4"));
        BigDecimal securityWeight = securityMetrics.getSecurityScore().multiply(new BigDecimal("0.6"));

        return complianceWeight.add(securityWeight);
    }

    /**
     * Check if immediate action is required
     */
    public boolean requiresImmediateAction() {
        return hasCriticalIssues() || 
               (riskAssessment != null && 
                ("HIGH".equals(riskAssessment.getOverallRiskLevel()) || 
                 "CRITICAL".equals(riskAssessment.getOverallRiskLevel())));
    }
}