package com.waqiti.common.compliance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Request object for submitting compliance reports to regulatory authorities
 * 
 * Encapsulates all information needed to submit reports through various
 * channels including electronic filing systems, secure portals, and direct submissions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceSubmissionRequest {

    /**
     * Report ID to submit
     */
    private String reportId;

    /**
     * Submission method
     */
    private SubmissionMethod submissionMethod;

    /**
     * Target regulatory authority
     */
    private String regulatoryAuthority;

    /**
     * Jurisdiction for submission
     */
    private String jurisdictionCode;

    /**
     * Submission deadline
     */
    private LocalDateTime submissionDeadline;

    /**
     * User requesting the submission
     */
    private String submittedBy;

    /**
     * Department submitting the report
     */
    private String submittingDepartment;

    /**
     * Submission priority
     */
    private SubmissionPriority priority;

    /**
     * Electronic filing system configuration
     */
    private ElectronicFilingConfig electronicFiling;

    /**
     * Document formatting requirements
     */
    private DocumentFormatting documentFormatting;

    /**
     * Digital signature configuration
     */
    private DigitalSignatureConfig digitalSignature;

    /**
     * Encryption requirements
     */
    private EncryptionConfig encryption;

    /**
     * Notification settings for submission
     */
    private SubmissionNotificationSettings notifications;

    /**
     * Retry configuration for failed submissions
     */
    private RetryConfiguration retryConfig;

    /**
     * Additional attachments to include
     */
    private List<SubmissionAttachment> attachments;

    /**
     * Custom metadata for submission
     */
    private Map<String, Object> metadata;

    /**
     * Submission parameters (for DTO compatibility)
     */
    private Map<String, String> submissionParameters;

    /**
     * Submission comments or notes
     */
    private String submissionComments;

    /**
     * Submission notes (alias for submissionComments for DTO compatibility)
     */
    private String submissionNotes;

    /**
     * Urgent submission flag
     */
    private boolean urgentSubmission;

    /**
     * Pre-submission validation requirements
     */
    private ValidationSettings validationSettings;

    /**
     * Audit trail configuration
     */
    private AuditSettings auditSettings;

    /**
     * Submission method enumeration
     */
    public enum SubmissionMethod {
        ELECTRONIC_FILING("Electronic Filing System", "Submit through regulatory electronic portal"),
        SECURE_EMAIL("Secure Email", "Submit via encrypted email"),
        PORTAL_UPLOAD("Web Portal", "Upload through regulatory web portal"),
        API_SUBMISSION("API Integration", "Submit via regulatory API"),
        MANUAL_DELIVERY("Manual Delivery", "Physical or manual submission required"),
        BULK_SUBMISSION("Bulk Submission", "Submit as part of bulk filing");

        private final String displayName;
        private final String description;

        SubmissionMethod(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    /**
     * Submission priority levels
     */
    public enum SubmissionPriority {
        URGENT("Urgent - Immediate submission required"),
        HIGH("High priority submission"),
        NORMAL("Normal priority submission"),
        LOW("Low priority submission"),
        BATCH("Batch processing acceptable");

        private final String description;

        SubmissionPriority(String description) {
            this.description = description;
        }

        public String getDescription() { return description; }
    }

    /**
     * Electronic filing system configuration
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ElectronicFilingConfig {
        private String filingSystemId;
        private String filingSystemName;
        private String endpointUrl;
        private String apiVersion;
        private Map<String, String> authenticationCredentials;
        private Map<String, Object> systemSpecificSettings;
        private String submissionFormat; // XML, JSON, CSV, etc.
        private String schemaVersion;
        private boolean testMode;
        private int timeoutSeconds;
        private Map<String, String> customHeaders;
    }

    /**
     * Document formatting requirements
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentFormatting {
        private String outputFormat; // PDF, XML, JSON, CSV
        private String templateId;
        private Map<String, Object> formatSettings;
        private boolean includeAttachments;
        private boolean generateCoverPage;
        private boolean includeTableOfContents;
        private String dateFormat;
        private String numberFormat;
        private String currencyFormat;
        private Map<String, String> customFormatting;
    }

    /**
     * Digital signature configuration
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DigitalSignatureConfig {
        private boolean required;
        private String certificateId;
        private String signatureAlgorithm;
        private String hashAlgorithm;
        private List<String> signatureFields;
        private boolean timestampRequired;
        private String timestampServer;
        private Map<String, Object> signatureMetadata;
    }

    /**
     * Encryption configuration
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EncryptionConfig {
        private boolean required;
        private String encryptionAlgorithm;
        private String keyId;
        private boolean encryptAttachments;
        private String publicKeyFingerprint;
        private Map<String, Object> encryptionParameters;
    }

    /**
     * Submission notification settings
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmissionNotificationSettings {
        private boolean emailOnSubmission;
        private boolean emailOnConfirmation;
        private boolean emailOnRejection;
        private boolean smsNotifications;
        private List<String> notificationRecipients;
        private List<String> escalationRecipients;
        private Map<String, Object> customNotificationSettings;
    }

    /**
     * Retry configuration
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetryConfiguration {
        private int maxRetries;
        private int retryDelaySeconds;
        private boolean exponentialBackoff;
        private List<String> retryableErrors;
        private boolean manualRetryRequired;
        private int maxRetryDurationHours;
    }

    /**
     * Submission attachment
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmissionAttachment {
        private String attachmentId;
        private String fileName;
        private String contentType;
        private byte[] content;
        private String description;
        private boolean required;
        private Map<String, String> metadata;
    }

    /**
     * Validation settings
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationSettings {
        private boolean validateBeforeSubmission;
        private boolean allowWarnings;
        private boolean blockOnErrors;
        private List<String> requiredValidations;
        private List<String> optionalValidations;
        private Map<String, Object> validationParameters;
    }

    /**
     * Audit settings
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditSettings {
        private boolean enableDetailedLogging;
        private boolean logSubmissionContent;
        private boolean logResponseContent;
        private String auditLevel; // BASIC, DETAILED, FULL
        private Map<String, Object> auditMetadata;
    }

    // Validation methods

    /**
     * Validate the submission request
     */
    public SubmissionValidation validate() {
        SubmissionValidation validation = new SubmissionValidation();

        // Required fields
        if (reportId == null || reportId.trim().isEmpty()) {
            validation.addError("reportId", "Report ID is required");
        }

        if (submissionMethod == null) {
            validation.addError("submissionMethod", "Submission method is required");
        }

        if (regulatoryAuthority == null || regulatoryAuthority.trim().isEmpty()) {
            validation.addError("regulatoryAuthority", "Regulatory authority is required");
        }

        if (submittedBy == null || submittedBy.trim().isEmpty()) {
            validation.addError("submittedBy", "Submitting user is required");
        }

        // Method-specific validation
        if (submissionMethod == SubmissionMethod.ELECTRONIC_FILING) {
            if (electronicFiling == null) {
                validation.addError("electronicFiling", "Electronic filing configuration required");
            } else {
                if (electronicFiling.getFilingSystemId() == null) {
                    validation.addError("electronicFiling.filingSystemId", "Filing system ID required");
                }
                if (electronicFiling.getEndpointUrl() == null) {
                    validation.addError("electronicFiling.endpointUrl", "Endpoint URL required");
                }
            }
        }

        // Digital signature validation
        if (digitalSignature != null && digitalSignature.isRequired()) {
            if (digitalSignature.getCertificateId() == null) {
                validation.addError("digitalSignature.certificateId", "Certificate ID required for digital signature");
            }
        }

        // Encryption validation
        if (encryption != null && encryption.isRequired()) {
            if (encryption.getKeyId() == null && encryption.getPublicKeyFingerprint() == null) {
                validation.addError("encryption", "Either key ID or public key fingerprint required for encryption");
            }
        }

        // Deadline validation
        if (submissionDeadline != null && submissionDeadline.isBefore(LocalDateTime.now())) {
            validation.addWarning("submissionDeadline", "Submission deadline is in the past");
        }

        // Retry configuration validation
        if (retryConfig != null) {
            if (retryConfig.getMaxRetries() < 0) {
                validation.addError("retryConfig.maxRetries", "Maximum retries cannot be negative");
            }
            if (retryConfig.getRetryDelaySeconds() < 0) {
                validation.addError("retryConfig.retryDelaySeconds", "Retry delay cannot be negative");
            }
        }

        return validation;
    }

    /**
     * Submission validation result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmissionValidation {
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

        public ValidationWarning(String field, String message) {
            this.field = field;
            this.message = message;
        }
    }

    // Utility methods

    /**
     * Create basic electronic filing request
     */
    public static ComplianceSubmissionRequest createElectronicFiling(String reportId, String authority, 
                                                                   String submittedBy, String systemId) {
        return ComplianceSubmissionRequest.builder()
            .reportId(reportId)
            .submissionMethod(SubmissionMethod.ELECTRONIC_FILING)
            .regulatoryAuthority(authority)
            .submittedBy(submittedBy)
            .priority(SubmissionPriority.NORMAL)
            .electronicFiling(ElectronicFilingConfig.builder()
                .filingSystemId(systemId)
                .testMode(false)
                .timeoutSeconds(300)
                .build())
            .validationSettings(ValidationSettings.builder()
                .validateBeforeSubmission(true)
                .blockOnErrors(true)
                .allowWarnings(true)
                .build())
            .build();
    }

    /**
     * Create urgent submission request
     */
    public static ComplianceSubmissionRequest createUrgentSubmission(String reportId, String authority, 
                                                                   String submittedBy) {
        return ComplianceSubmissionRequest.builder()
            .reportId(reportId)
            .submissionMethod(SubmissionMethod.ELECTRONIC_FILING)
            .regulatoryAuthority(authority)
            .submittedBy(submittedBy)
            .priority(SubmissionPriority.URGENT)
            .submissionDeadline(LocalDateTime.now().plusHours(4)) // 4-hour deadline for urgent
            .notifications(SubmissionNotificationSettings.builder()
                .emailOnSubmission(true)
                .emailOnConfirmation(true)
                .emailOnRejection(true)
                .build())
            .retryConfig(RetryConfiguration.builder()
                .maxRetries(3)
                .retryDelaySeconds(60)
                .exponentialBackoff(true)
                .build())
            .build();
    }
}