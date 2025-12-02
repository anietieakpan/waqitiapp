package com.waqiti.payment.audit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Enterprise Compliance Report
 * 
 * Comprehensive compliance reporting for regulatory requirements including:
 * - PCI-DSS compliance status
 * - AML/KYC compliance metrics
 * - Data privacy compliance (GDPR, CCPA)
 * - Transaction monitoring compliance
 * - Audit trail completeness
 * 
 * @version 2.0.0
 * @since 2025-01-18
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceReport {
    
    // Report identification
    private String reportId;
    private String reportType;
    private LocalDateTime generatedAt;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private String generatedBy;
    
    // Overall compliance status
    private ComplianceStatus overallStatus;
    private double complianceScore;
    private Map<String, ComplianceStatus> standardCompliance;
    
    // PCI-DSS compliance
    private PCIComplianceSection pciCompliance;
    
    // AML/KYC compliance
    private AMLKYCComplianceSection amlKycCompliance;
    
    // Data privacy compliance
    private DataPrivacyComplianceSection dataPrivacyCompliance;
    
    // Transaction monitoring
    private TransactionMonitoringSection transactionMonitoring;
    
    // Audit trail compliance
    private AuditTrailComplianceSection auditTrailCompliance;
    
    // Violations and remediation
    private List<ComplianceViolation> violations;
    private List<RemediationAction> remediationActions;
    private Map<String, String> evidenceLinks;
    
    // Executive summary
    private String executiveSummary;
    private List<String> keyFindings;
    private List<String> recommendations;
    
    // Certification status
    private boolean certificationReady;
    private LocalDateTime nextAuditDate;
    private String auditorName;
    
    // Enums
    public enum ComplianceStatus {
        COMPLIANT,
        PARTIALLY_COMPLIANT,
        NON_COMPLIANT,
        NOT_APPLICABLE,
        PENDING_REVIEW
    }
    
    // Supporting classes
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PCIComplianceSection {
        private ComplianceStatus status;
        private String pciLevel;
        private LocalDateTime lastAssessmentDate;
        private Map<String, Boolean> requirementsMet;
        private List<String> failedControls;
        private int encryptedTransactions;
        private int unencryptedTransactions;
        private boolean cardDataMasked;
        private boolean tokenizationEnabled;
        private List<SecurityControl> implementedControls;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AMLKYCComplianceSection {
        private ComplianceStatus status;
        private int totalCustomers;
        private int verifiedCustomers;
        private int pendingVerification;
        private int highRiskCustomers;
        private Map<String, Integer> verificationLevels;
        private List<SARFiling> sarFilings;
        private int suspiciousTransactionsReported;
        private BigDecimal totalReportedAmount;
        private boolean sanctionsScreeningEnabled;
        private LocalDateTime lastScreeningUpdate;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataPrivacyComplianceSection {
        private ComplianceStatus gdprStatus;
        private ComplianceStatus ccpaStatus;
        private int dataSubjectRequests;
        private int completedRequests;
        private double averageResponseTimeDays;
        private boolean consentManagementEnabled;
        private boolean dataRetentionPolicyCompliant;
        private int dataBreachIncidents;
        private List<DataBreachReport> breachReports;
        private Map<String, Boolean> privacyControls;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionMonitoringSection {
        private ComplianceStatus status;
        private long totalTransactionsMonitored;
        private int flaggedTransactions;
        private int investigatedTransactions;
        private int falsePositives;
        private double detectionAccuracy;
        private Map<String, Integer> alertsByType;
        private List<MonitoringRule> activeRules;
        private LocalDateTime lastRuleUpdate;
        private boolean realTimeMonitoringEnabled;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditTrailComplianceSection {
        private ComplianceStatus status;
        private long totalAuditRecords;
        private double auditCoveragePercentage;
        private boolean tamperProofStorage;
        private int retentionPeriodYears;
        private Map<String, Long> recordsByCategory;
        private List<String> missingAuditTypes;
        private boolean externalBackupEnabled;
        private LocalDateTime lastIntegrityCheck;
        private boolean integrityCheckPassed;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceViolation {
        private String violationId;
        private String violationType;
        private String standard;
        private String description;
        private LocalDateTime detectedAt;
        private String severity;
        private BigDecimal potentialFine;
        private String status;
        private String assignedTo;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RemediationAction {
        private String actionId;
        private String violationId;
        private String actionDescription;
        private String priority;
        private LocalDateTime dueDate;
        private String responsibleParty;
        private String status;
        private BigDecimal estimatedCost;
        private String verificationMethod;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityControl {
        private String controlId;
        private String controlName;
        private String category;
        private boolean implemented;
        private LocalDateTime implementedDate;
        private LocalDateTime lastTestedDate;
        private String effectiveness;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SARFiling {
        private String filingId;
        private LocalDateTime filingDate;
        private String filingReason;
        private BigDecimal suspiciousAmount;
        private String status;
        private String regulatoryBody;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataBreachReport {
        private String incidentId;
        private LocalDateTime occurredAt;
        private LocalDateTime reportedAt;
        private int affectedRecords;
        private String dataTypes;
        private boolean regulatorNotified;
        private boolean customersNotified;
        private String remediationStatus;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonitoringRule {
        private String ruleId;
        private String ruleName;
        private String ruleType;
        private boolean active;
        private int triggerCount;
        private double falsePositiveRate;
        private LocalDateTime lastTriggered;
        private String threshold;
    }
}