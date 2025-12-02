package com.waqiti.common.compliance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Request object for creating or updating compliance reports
 * 
 * Used by compliance officers and automated systems to initiate
 * the creation of various regulatory compliance reports.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceReportRequest {

    /**
     * Type of compliance report to create
     */
    private ComplianceReportType reportType;

    /**
     * Title for the report
     */
    private String title;

    /**
     * Detailed description of the report purpose
     */
    private String description;

    /**
     * Priority level for the report
     */
    private ComplianceReport.ReportPriority priority;

    /**
     * Regulatory authority jurisdiction
     */
    private String regulatoryAuthority;

    /**
     * Jurisdiction code (US, EU, UK, etc.)
     */
    private String jurisdictionCode;

    /**
     * Custom filing deadline (overrides default if specified)
     */
    private LocalDateTime customFilingDeadline;

    /**
     * Report period start date
     */
    private LocalDateTime reportPeriodStart;

    /**
     * Report period end date
     */
    private LocalDateTime reportPeriodEnd;

    /**
     * User requesting the report creation
     */
    private String requestedBy;

    /**
     * User who generated the report (may be different from requester for automated reports)
     */
    private String generatedBy;

    /**
     * Report period identifier (e.g., "Q1-2024", "FY-2023")
     */
    private String reportPeriod;

    /**
     * Department or team requesting the report
     */
    private String requestingDepartment;

    /**
     * Business justification for the report
     */
    private String businessJustification;

    /**
     * List of transaction IDs to include in the report
     */
    private List<String> transactionIds;

    /**
     * List of customer IDs to include in the report
     */
    private List<String> customerIds;

    /**
     * List of account IDs to include in the report
     */
    private List<String> accountIds;

    /**
     * Specific compliance flags to investigate
     */
    private List<String> complianceFlags;

    /**
     * Additional search criteria for data inclusion
     */
    private ReportDataCriteria dataCriteria;

    /**
     * Template to use for report generation
     */
    private String templateId;

    /**
     * Custom report sections to include
     */
    private List<String> customSections;

    /**
     * Auto-generation settings
     */
    private AutoGenerationSettings autoGeneration;

    /**
     * Workflow configuration
     */
    private WorkflowConfiguration workflow;

    /**
     * Tags for report categorization
     */
    private List<String> tags;

    /**
     * Custom metadata
     */
    private Map<String, Object> metadata;

    /**
     * Additional parameters for report generation
     */
    private Map<String, String> parameters;

    /**
     * External system references
     */
    private List<ExternalSystemReference> externalReferences;

    /**
     * Notification settings
     */
    private NotificationSettings notifications;

    /**
     * Report configuration overrides
     */
    private Map<String, Object> configurationOverrides;

    /**
     * Quality assurance requirements
     */
    private QualityAssuranceSettings qualityAssurance;

    /**
     * Data retention requirements
     */
    private DataRetentionSettings dataRetention;

    /**
     * Report data selection criteria
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportDataCriteria {
        private LocalDateTime transactionDateFrom;
        private LocalDateTime transactionDateTo;
        private List<String> transactionTypes;
        private List<String> currencies;
        private Double minimumAmount;
        private Double maximumAmount;
        private List<String> riskLevels;
        private List<String> customerTypes;
        private List<String> geographicRegions;
        private List<String> merchantIds;
        private List<String> productTypes;
        private Boolean suspiciousActivityOnly;
        private Boolean highRiskCustomersOnly;
        private List<String> complianceFlagTypes;
        private Map<String, Object> customFilters;
    }

    /**
     * Auto-generation settings
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AutoGenerationSettings {
        private boolean enabled;
        private String schedule; // Cron expression
        private String triggerEvent;
        private Map<String, Object> triggerCriteria;
        private boolean requiresHumanReview;
        private boolean autoSubmit;
        private String generationTemplate;
        private List<String> dataSourcesRequired;
    }

    /**
     * Workflow configuration
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowConfiguration {
        private boolean requiresComplianceReview;
        private boolean requiresLegalReview;
        private boolean requiresManagementApproval;
        private List<String> requiredApprovers;
        private int minimumApprovals;
        private boolean allowParallelReview;
        private String escalationPolicy;
        private int autoEscalationHours;
        private Map<String, Object> customWorkflowSteps;
    }

    /**
     * External system reference
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExternalSystemReference {
        private String systemName;
        private String referenceType;
        private String referenceId;
        private String url;
        private Map<String, String> additionalData;
        private boolean requiresSync;
    }

    /**
     * Notification settings
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationSettings {
        private boolean emailNotifications;
        private boolean smsNotifications;
        private boolean slackNotifications;
        private List<String> notificationRecipients;
        private List<String> escalationRecipients;
        private NotificationTiming timing;
        private Map<String, Object> customNotifications;
    }

    /**
     * Notification timing
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationTiming {
        private boolean onCreation;
        private boolean onStatusChange;
        private boolean onApproval;
        private boolean onSubmission;
        private boolean onDeadlineApproaching;
        private boolean onOverdue;
        private int deadlineWarningDays;
    }

    /**
     * Quality assurance settings
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QualityAssuranceSettings {
        private boolean enableDataValidation;
        private boolean requiresPeerReview;
        private double minimumQualityScore;
        private List<String> mandatoryChecks;
        private boolean enableAutomatedTesting;
        private String qualityTemplate;
        private Map<String, Object> customQualityRules;
    }

    /**
     * Data retention settings
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataRetentionSettings {
        private int retentionYears;
        private boolean enableArchiving;
        private LocalDateTime customExpirationDate;
        private String archiveLocation;
        private boolean encryptionRequired;
        private String retentionReason;
        private Map<String, Object> customRetentionRules;
    }

    // Validation methods

    /**
     * Validate the report request
     */
    public RequestValidation validate() {
        RequestValidation validation = new RequestValidation();

        // Required fields validation
        if (reportType == null) {
            validation.addError("reportType", "Report type is required");
        }

        if (title == null || title.trim().isEmpty()) {
            validation.addError("title", "Report title is required");
        }

        if (requestedBy == null || requestedBy.trim().isEmpty()) {
            validation.addError("requestedBy", "Requesting user is required");
        }

        // Business logic validation
        if (reportPeriodStart != null && reportPeriodEnd != null) {
            if (reportPeriodStart.isAfter(reportPeriodEnd)) {
                validation.addError("reportPeriod", "Report period start must be before end date");
            }
        }

        if (customFilingDeadline != null && customFilingDeadline.isBefore(LocalDateTime.now())) {
            validation.addWarning("customFilingDeadline", "Custom filing deadline is in the past");
        }

        // Data criteria validation
        if (dataCriteria != null) {
            if (dataCriteria.getMinimumAmount() != null && dataCriteria.getMaximumAmount() != null) {
                if (dataCriteria.getMinimumAmount() > dataCriteria.getMaximumAmount()) {
                    validation.addError("dataCriteria.amount", "Minimum amount cannot be greater than maximum amount");
                }
            }
        }

        // Auto-generation validation
        if (autoGeneration != null && autoGeneration.isEnabled()) {
            if (autoGeneration.getSchedule() == null && autoGeneration.getTriggerEvent() == null) {
                validation.addError("autoGeneration", "Either schedule or trigger event must be specified for auto-generation");
            }
        }

        return validation;
    }

    /**
     * Request validation result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestValidation {
        @Builder.Default
        private List<ValidationError> errors = new java.util.ArrayList<>();
        @Builder.Default
        private List<ValidationWarning> warnings = new java.util.ArrayList<>();

        public void addError(String field, String message) {
            errors.add(new ValidationError(field, message));
        }

        public void addWarning(String field, String message) {
            warnings.add(new ValidationWarning(field, message));
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }

    /**
     * Validation error
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationError {
        private String field;
        private String message;
        private String errorCode;
        private String suggestedFix;

        public ValidationError(String field, String message) {
            this.field = field;
            this.message = message;
        }
    }

    /**
     * Validation warning
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationWarning {
        private String field;
        private String message;
        private String warningCode;
        private String recommendation;

        public ValidationWarning(String field, String message) {
            this.field = field;
            this.message = message;
        }
    }

    // Utility methods

    /**
     * Create a basic SAR report request
     */
    public static ComplianceReportRequest createSARRequest(String title, String requestedBy, List<String> transactionIds) {
        return ComplianceReportRequest.builder()
            .reportType(ComplianceReportType.SAR)
            .title(title)
            .requestedBy(requestedBy)
            .priority(ComplianceReport.ReportPriority.HIGH)
            .transactionIds(transactionIds)
            .regulatoryAuthority("FinCEN")
            .jurisdictionCode("US")
            .build();
    }

    /**
     * Create a basic CTR report request
     */
    public static ComplianceReportRequest createCTRRequest(String title, String requestedBy, List<String> transactionIds) {
        return ComplianceReportRequest.builder()
            .reportType(ComplianceReportType.CTR)
            .title(title)
            .requestedBy(requestedBy)
            .priority(ComplianceReport.ReportPriority.HIGH)
            .transactionIds(transactionIds)
            .regulatoryAuthority("FinCEN")
            .jurisdictionCode("US")
            .build();
    }

    /**
     * Create an AML compliance report request
     */
    public static ComplianceReportRequest createAMLReportRequest(String title, String requestedBy, 
                                                               LocalDateTime periodStart, LocalDateTime periodEnd) {
        return ComplianceReportRequest.builder()
            .reportType(ComplianceReportType.AML_COMPLIANCE)
            .title(title)
            .requestedBy(requestedBy)
            .priority(ComplianceReport.ReportPriority.MEDIUM)
            .reportPeriodStart(periodStart)
            .reportPeriodEnd(periodEnd)
            .regulatoryAuthority("Multiple")
            .jurisdictionCode("US")
            .build();
    }

    /**
     * Get estimated completion time based on report type and complexity
     */
    public int getEstimatedCompletionHours() {
        if (reportType == null) return 24; // Default

        int baseHours = switch (reportType) {
            case SAR -> 8;
            case CTR -> 4;
            case AML_COMPLIANCE -> 72;
            case KYC_COMPLIANCE -> 48;
            case CYBERSECURITY_INCIDENT -> 2;
            case RISK_ASSESSMENT -> 120;
            default -> 24;
        };

        // Adjust based on complexity factors
        int complexityMultiplier = 1;
        
        if (transactionIds != null && transactionIds.size() > 100) complexityMultiplier++;
        if (customerIds != null && customerIds.size() > 50) complexityMultiplier++;
        if (dataCriteria != null && dataCriteria.getCustomFilters() != null && 
            !dataCriteria.getCustomFilters().isEmpty()) complexityMultiplier++;

        return baseHours * complexityMultiplier;
    }
}