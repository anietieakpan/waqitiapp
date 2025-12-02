package com.waqiti.common.compliance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive compliance report entity for Waqiti regulatory reporting system
 * 
 * Supports various regulatory reports including:
 * - Suspicious Activity Reports (SAR)
 * - Currency Transaction Reports (CTR)
 * - Large Cash Transaction Reports (LCTR)
 * - Anti-Money Laundering (AML) reports
 * - Know Your Customer (KYC) compliance reports
 * - Regulatory audit reports
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceReport {

    /**
     * Unique report identifier
     */
    private String reportId;

    /**
     * Type of compliance report
     */
    private ComplianceReportType reportType;

    /**
     * Current status of the report
     */
    private ComplianceReportStatus status;

    /**
     * Title/subject of the report
     */
    private String title;

    /**
     * Detailed description of the report
     */
    private String description;

    /**
     * Priority level of the report
     */
    private ReportPriority priority;

    /**
     * Regulatory authority or jurisdiction
     */
    private String regulatoryAuthority;

    /**
     * Jurisdiction code (US, EU, UK, etc.)
     */
    private String jurisdictionCode;

    /**
     * Report filing deadline
     */
    private LocalDateTime filingDeadline;

    /**
     * Report period start date
     */
    private LocalDateTime reportPeriodStart;

    /**
     * Report period end date
     */
    private LocalDateTime reportPeriodEnd;

    /**
     * Report period description (for display and reporting)
     */
    private String reportPeriod;

    /**
     * User ID who created the report
     */
    private String createdBy;

    /**
     * User ID who generated/authored the report (alias for createdBy for compatibility)
     */
    private String generatedBy;

    /**
     * User ID who last modified the report
     */
    private String modifiedBy;

    /**
     * Report creation timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Report generation timestamp (alias for createdAt for compatibility)
     */
    private LocalDateTime generatedAt;

    /**
     * Last modification timestamp
     */
    private LocalDateTime modifiedAt;

    /**
     * Report submission timestamp
     */
    private LocalDateTime submittedAt;

    /**
     * Report approval timestamp
     */
    private LocalDateTime approvedAt;

    /**
     * User ID who approved the report
     */
    private String approvedBy;

    /**
     * Report filing timestamp with authority
     */
    private LocalDateTime filedAt;

    /**
     * Confirmation/reference number from regulatory authority
     */
    private String filingConfirmationNumber;

    /**
     * Main report content and findings
     */
    private ReportContent content;

    /**
     * List of transactions included in this report
     */
    private List<TransactionReference> transactions;

    /**
     * List of customers/entities referenced in the report
     */
    private List<CustomerReference> customers;

    /**
     * Supporting documents attached to the report
     */
    private List<SupportingDocument> supportingDocuments;

    /**
     * Compliance flags and risk indicators
     */
    private List<ComplianceFlag> complianceFlags;

    /**
     * Report validation results
     */
    private ReportValidation validation;

    /**
     * Report validation results (alias for validation for compatibility)
     */
    private com.waqiti.common.compliance.dto.ComplianceDTOs.ComplianceValidationResult validationResult;

    /**
     * Report storage path in secure file system
     */
    private String storagePath;

    /**
     * Report file size in bytes
     */
    private Long fileSize;

    /**
     * Report checksum for integrity verification
     */
    private String checksum;

    /**
     * Whether the report is encrypted at rest
     */
    private Boolean encrypted;

    /**
     * Error message if report generation failed
     */
    private String error;

    /**
     * Report quality score (0-100)
     */
    private Integer qualityScore;

    /**
     * Risk assessment summary
     */
    private RiskAssessment riskAssessment;

    /**
     * Financial summary and totals
     */
    private FinancialSummary financialSummary;

    /**
     * Workflow and approval history
     */
    private List<WorkflowStep> workflowHistory;

    /**
     * Comments and notes from reviewers
     */
    private List<ReportComment> comments;

    /**
     * Report tags for categorization
     */
    private List<String> tags;

    /**
     * Custom metadata and attributes
     */
    private Map<String, Object> metadata;

    /**
     * Report configuration settings
     */
    private ReportConfiguration configuration;

    /**
     * Auto-generation settings if applicable
     */
    private AutoGenerationConfig autoGeneration;

    /**
     * External system references
     */
    private List<ExternalReference> externalReferences;

    /**
     * Report metrics and statistics
     */
    private ReportMetrics metrics;

    /**
     * Data retention policy
     */
    private DataRetentionPolicy retentionPolicy;

    /**
     * Report content and findings
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportContent {
        private String executiveSummary;
        private String detailedFindings;
        private String recommendations;
        private String legalAnalysis;
        private String riskAnalysis;
        private List<String> keyFindings;
        private List<RecommendationItem> actionItems;
        private String conclusion;
        private Map<String, Object> customSections;
    }

    /**
     * Transaction reference in report
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionReference {
        private String transactionId;
        private String transactionType;
        private BigDecimal amount;
        private String currency;
        private LocalDateTime timestamp;
        private String fromAccount;
        private String toAccount;
        private String suspiciousActivity;
        private BigDecimal riskScore;
        private Map<String, String> attributes;
    }

    /**
     * Customer reference in report
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerReference {
        private String customerId;
        private String customerType;
        private String fullName;
        private String businessName;
        private String identificationNumber;
        private String address;
        private String riskCategory;
        private List<String> riskFactors;
        private BigDecimal riskScore;
        private Map<String, String> attributes;
    }

    /**
     * Supporting document attachment
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupportingDocument {
        private String documentId;
        private String documentType;
        private String fileName;
        private String fileUrl;
        private long fileSize;
        private String mimeType;
        private String checksum;
        private LocalDateTime uploadedAt;
        private String uploadedBy;
        private String description;
        private boolean encrypted;
        private Map<String, String> metadata;
    }

    /**
     * Compliance flag or alert
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceFlag {
        private String flagId;
        private String flagType;
        private String severity;
        private String description;
        private String reason;
        private LocalDateTime flaggedAt;
        private String flaggedBy;
        private boolean resolved;
        private LocalDateTime resolvedAt;
        private String resolvedBy;
        private String resolution;
    }

    /**
     * Report validation results
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportValidation {
        private boolean isValid;
        private List<ValidationError> errors;
        private List<ValidationWarning> warnings;
        private LocalDateTime validatedAt;
        private String validatedBy;
        private String validationVersion;
        private int errorCount;
        private int warningCount;
    }

    /**
     * Risk assessment summary
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskAssessment {
        private BigDecimal overallRiskScore;
        private String riskLevel;
        private List<RiskFactor> riskFactors;
        private String assessmentMethodology;
        private LocalDateTime assessedAt;
        private String assessedBy;
        private String riskMitigation;
        private boolean requiresEscalation;
    }

    /**
     * Financial summary and totals
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinancialSummary {
        private BigDecimal totalAmount;
        private String baseCurrency;
        private int transactionCount;
        private int customerCount;
        private BigDecimal averageTransactionAmount;
        private BigDecimal maximumTransactionAmount;
        private BigDecimal minimumTransactionAmount;
        private Map<String, BigDecimal> currencyBreakdown;
        private Map<String, BigDecimal> transactionTypeBreakdown;
        private LocalDateTime calculatedAt;
    }

    /**
     * Workflow step in approval process
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowStep {
        private String stepId;
        private String stepName;
        private String stepType;
        private String assignedTo;
        private String completedBy;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private String status;
        private String action;
        private String comments;
        private Map<String, String> stepData;
    }

    /**
     * Report comment from reviewer
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportComment {
        private String commentId;
        private String commentType;
        private String content;
        private String authorId;
        private String authorName;
        private LocalDateTime createdAt;
        private LocalDateTime modifiedAt;
        private String section;
        private boolean resolved;
        private String parentCommentId;
        private List<String> attachments;
    }

    /**
     * Report configuration settings
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportConfiguration {
        private boolean autoApproval;
        private List<String> requiredApprovers;
        private int minimumApprovals;
        private boolean requiresLegalReview;
        private boolean requiresComplianceReview;
        private LocalDateTime configVersion;
        private Map<String, Object> customSettings;
    }

    /**
     * Auto-generation configuration
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AutoGenerationConfig {
        private boolean enabled;
        private String triggerType;
        private Map<String, Object> triggerCriteria;
        private String generationTemplate;
        private LocalDateTime lastGenerated;
        private String generationFrequency;
        private boolean requiresHumanReview;
    }

    /**
     * External system reference
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExternalReference {
        private String systemName;
        private String referenceType;
        private String referenceId;
        private String url;
        private LocalDateTime syncedAt;
        private String syncStatus;
        private Map<String, String> additionalData;
    }

    /**
     * Report metrics and statistics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportMetrics {
        private long processingTimeMs;
        private int dataSourcesCount;
        private LocalDateTime dataFreshnessTimestamp;
        private double completenessScore;
        private double accuracyScore;
        private int revisionsCount;
        private Map<String, Object> customMetrics;
    }

    /**
     * Data retention policy
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataRetentionPolicy {
        private int retentionYears;
        private LocalDateTime expirationDate;
        private boolean archived;
        private LocalDateTime archivedAt;
        private String archiveLocation;
        private boolean canPurge;
        private String retentionReason;
    }

    /**
     * Recommendation item
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendationItem {
        private String recommendationId;
        private String category;
        private String description;
        private String priority;
        private String assignedTo;
        private LocalDateTime dueDate;
        private String status;
        private LocalDateTime completedAt;
    }

    /**
     * Risk factor detail
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskFactor {
        private String factorType;
        private String description;
        private BigDecimal weight;
        private BigDecimal score;
        private String impact;
        private String mitigation;
    }

    /**
     * Validation error detail
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationError {
        private String errorCode;
        private String field;
        private String message;
        private String severity;
        private String suggestedFix;
    }

    /**
     * Validation warning detail
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationWarning {
        private String warningCode;
        private String field;
        private String message;
        private String recommendation;
    }

    /**
     * Report priority levels
     */
    public enum ReportPriority {
        CRITICAL("Critical - Immediate action required"),
        HIGH("High priority"),
        MEDIUM("Medium priority"),
        LOW("Low priority"),
        ROUTINE("Routine filing");

        private final String description;

        ReportPriority(String description) {
            this.description = description;
        }

        public String getDescription() { return description; }
    }

    // Utility methods

    /**
     * Check if report is overdue
     */
    public boolean isOverdue() {
        return filingDeadline != null && 
               LocalDateTime.now().isAfter(filingDeadline) && 
               !isSubmitted();
    }

    /**
     * Check if report is submitted
     */
    public boolean isSubmitted() {
        return status == ComplianceReportStatus.SUBMITTED ||
               status == ComplianceReportStatus.FILED ||
               status == ComplianceReportStatus.APPROVED;
    }

    /**
     * Check if report is in draft status
     */
    public boolean isDraft() {
        return status == ComplianceReportStatus.DRAFT ||
               status == ComplianceReportStatus.IN_PROGRESS;
    }

    /**
     * Get days until filing deadline
     */
    public long getDaysUntilDeadline() {
        if (filingDeadline == null) return -1;
        return java.time.Duration.between(LocalDateTime.now(), filingDeadline).toDays();
    }

    /**
     * Get completion percentage
     */
    public int getCompletionPercentage() {
        if (metrics != null) {
            return (int) (metrics.getCompletenessScore() * 100);
        }
        return 0;
    }

    /**
     * Check if report requires urgent attention
     */
    public boolean requiresUrgentAttention() {
        return priority == ReportPriority.CRITICAL ||
               isOverdue() ||
               (getDaysUntilDeadline() <= 1 && !isSubmitted());
    }
}