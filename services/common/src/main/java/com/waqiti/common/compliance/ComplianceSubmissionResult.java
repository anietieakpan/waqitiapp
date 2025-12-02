package com.waqiti.common.compliance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Result object for compliance report submission operations
 * 
 * Contains the outcome of submitting reports to regulatory authorities,
 * including success/failure status, confirmation details, and any error information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceSubmissionResult {

    /**
     * Report ID that was submitted
     */
    private String reportId;

    /**
     * Unique submission ID
     */
    private String submissionId;

    /**
     * Submission success status
     */
    private boolean success;

    /**
     * Submission status
     */
    private SubmissionStatus submissionStatus;

    /**
     * Regulatory authority confirmation number
     */
    private String confirmationNumber;

    /**
     * Regulatory reference ID
     */
    private String regulatoryReferenceId;

    /**
     * Submission timestamp
     */
    private LocalDateTime submittedAt;

    /**
     * Acknowledgment timestamp from regulatory authority
     */
    private LocalDateTime acknowledgedAt;

    /**
     * Processing timestamp by regulatory authority
     */
    private LocalDateTime processedAt;

    /**
     * User who initiated the submission
     */
    private String submittedBy;

    /**
     * Submission method used
     */
    private ComplianceSubmissionRequest.SubmissionMethod submissionMethod;

    /**
     * Target regulatory authority
     */
    private String regulatoryAuthority;

    /**
     * Status message from submission
     */
    private String statusMessage;

    /**
     * Submission response from regulatory system
     */
    private SubmissionResponse submissionResponse;

    /**
     * Error information if submission failed
     */
    private SubmissionError submissionError;

    /**
     * Validation results
     */
    private SubmissionValidationResult validationResult;

    /**
     * Processing metrics
     */
    private SubmissionMetrics metrics;

    /**
     * Files submitted
     */
    private List<SubmittedFile> submittedFiles;

    /**
     * Receipt or proof of submission
     */
    private SubmissionReceipt receipt;

    /**
     * Follow-up actions required
     */
    private List<FollowUpAction> followUpActions;

    /**
     * Next steps or recommendations
     */
    private List<String> nextSteps;

    /**
     * Additional metadata from submission
     */
    private Map<String, Object> metadata;

    /**
     * Retry information if applicable
     */
    private RetryInformation retryInfo;

    /**
     * Audit trail for submission
     */
    private List<SubmissionAuditEntry> auditTrail;

    /**
     * External system references
     */
    private List<ExternalSystemReference> externalReferences;

    /**
     * Submission status enumeration
     */
    public enum SubmissionStatus {
        SUBMITTED("Submitted", "Successfully submitted to regulatory authority"),
        ACKNOWLEDGED("Acknowledged", "Submission acknowledged by regulatory authority"),
        PROCESSING("Processing", "Submission being processed by regulatory authority"),
        ACCEPTED("Accepted", "Submission accepted and filed"),
        REJECTED("Rejected", "Submission rejected by regulatory authority"),
        FAILED("Failed", "Submission failed due to technical error"),
        PENDING_REVIEW("Pending Review", "Submission pending manual review"),
        CANCELLED("Cancelled", "Submission was cancelled"),
        EXPIRED("Expired", "Submission expired before processing");

        private final String displayName;
        private final String description;

        SubmissionStatus(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    /**
     * Submission response from regulatory system
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmissionResponse {
        private int httpStatusCode;
        private String responseMessage;
        private String responseCode;
        private Map<String, String> responseHeaders;
        private String rawResponse;
        private LocalDateTime responseTimestamp;
        private long responseTimeMs;
        private String serverVersion;
        private Map<String, Object> parsedResponse;
    }

    /**
     * Submission error information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmissionError {
        private String errorCode;
        private String errorMessage;
        private String errorCategory;
        private ErrorSeverity severity;
        private boolean retryable;
        private String resolution;
        private List<String> affectedFields;
        private String technicalDetails;
        private LocalDateTime errorTimestamp;
        private Map<String, Object> errorMetadata;

        public enum ErrorSeverity {
            LOW, MEDIUM, HIGH, CRITICAL
        }
    }

    /**
     * Submission validation result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmissionValidationResult {
        private boolean valid;
        private List<ValidationIssue> errors;
        private List<ValidationIssue> warnings;
        private List<ValidationIssue> informationalMessages;
        private String validationSummary;
        private LocalDateTime validatedAt;
        private String validatorVersion;
    }

    /**
     * Validation issue
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationIssue {
        private String issueCode;
        private String message;
        private String field;
        private String severity;
        private String suggestedFix;
        private Map<String, Object> context;
    }

    /**
     * Submission processing metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmissionMetrics {
        private long totalProcessingTimeMs;
        private long validationTimeMs;
        private long formatConversionTimeMs;
        private long encryptionTimeMs;
        private long transmissionTimeMs;
        private long responseTimeMs;
        private int retryAttempts;
        private long totalRetryTimeMs;
        private int fileSizeBytes;
        private int attachmentCount;
        private Map<String, Object> additionalMetrics;
    }

    /**
     * Submitted file information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmittedFile {
        private String fileName;
        private String fileType;
        private String contentType;
        private long fileSizeBytes;
        private String fileHash;
        private String encryptionStatus;
        private String signatureStatus;
        private LocalDateTime submittedAt;
        private Map<String, String> fileMetadata;
    }

    /**
     * Submission receipt
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmissionReceipt {
        private String receiptId;
        private String receiptNumber;
        private byte[] receiptDocument;
        private String receiptUrl;
        private String receiptFormat; // PDF, HTML, XML
        private LocalDateTime generatedAt;
        private String digitalSignature;
        private LocalDateTime expiresAt;
        private Map<String, Object> receiptMetadata;
    }

    /**
     * Follow-up action required
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FollowUpAction {
        private String actionId;
        private String actionType;
        private String description;
        private String assignedTo;
        private LocalDateTime dueDate;
        private String priority;
        private String status;
        private Map<String, Object> actionData;
    }

    /**
     * Retry information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetryInformation {
        private int attemptNumber;
        private int maxAttempts;
        private LocalDateTime lastAttemptAt;
        private LocalDateTime nextRetryAt;
        private String retryReason;
        private List<String> previousErrors;
        private boolean finalAttempt;
        private String retryStrategy;
    }

    /**
     * Submission audit entry
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmissionAuditEntry {
        private String entryId;
        private LocalDateTime timestamp;
        private String action;
        private String userId;
        private String description;
        private String component;
        private Map<String, Object> details;
        private String ipAddress;
        private String userAgent;
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
        private LocalDateTime createdAt;
        private Map<String, String> additionalData;
    }

    // Utility methods

    /**
     * Check if submission was successful
     */
    public boolean isSuccessful() {
        return success && (submissionStatus == SubmissionStatus.SUBMITTED ||
                          submissionStatus == SubmissionStatus.ACKNOWLEDGED ||
                          submissionStatus == SubmissionStatus.ACCEPTED);
    }

    /**
     * Check if submission failed
     */
    public boolean isFailed() {
        return !success || submissionStatus == SubmissionStatus.FAILED ||
               submissionStatus == SubmissionStatus.REJECTED;
    }

    /**
     * Check if submission is in progress
     */
    public boolean isInProgress() {
        return submissionStatus == SubmissionStatus.PROCESSING ||
               submissionStatus == SubmissionStatus.PENDING_REVIEW;
    }

    /**
     * Check if submission requires retry
     */
    public boolean requiresRetry() {
        return isFailed() && submissionError != null && submissionError.isRetryable() &&
               (retryInfo == null || !retryInfo.isFinalAttempt());
    }

    /**
     * Get status display text
     */
    public String getStatusDisplayText() {
        if (submissionStatus == null) return "Unknown";
        return submissionStatus.getDisplayName();
    }

    /**
     * Get error summary
     */
    public String getErrorSummary() {
        if (submissionError == null) return null;
        return submissionError.getErrorMessage();
    }

    /**
     * Get validation issues summary
     */
    public String getValidationIssuesSummary() {
        if (validationResult == null) return "No validation performed";
        
        int errors = validationResult.getErrors() != null ? validationResult.getErrors().size() : 0;
        int warnings = validationResult.getWarnings() != null ? validationResult.getWarnings().size() : 0;
        
        if (errors == 0 && warnings == 0) return "No issues";
        
        StringBuilder summary = new StringBuilder();
        if (errors > 0) {
            summary.append(errors).append(" error");
            if (errors > 1) summary.append("s");
        }
        if (warnings > 0) {
            if (summary.length() > 0) summary.append(", ");
            summary.append(warnings).append(" warning");
            if (warnings > 1) summary.append("s");
        }
        
        return summary.toString();
    }

    /**
     * Get processing time summary
     */
    public String getProcessingTimeSummary() {
        if (metrics == null) return "No metrics available";
        
        long totalMs = metrics.getTotalProcessingTimeMs();
        if (totalMs < 1000) return totalMs + "ms";
        if (totalMs < 60000) return String.format("%.1fs", totalMs / 1000.0);
        return String.format("%.1fm", totalMs / 60000.0);
    }

    /**
     * Check if submission has receipt
     */
    public boolean hasReceipt() {
        return receipt != null && (receipt.getReceiptDocument() != null || receipt.getReceiptUrl() != null);
    }

    /**
     * Check if submission has follow-up actions
     */
    public boolean hasFollowUpActions() {
        return followUpActions != null && !followUpActions.isEmpty();
    }

    /**
     * Get next action due date
     */
    public LocalDateTime getNextActionDueDate() {
        if (!hasFollowUpActions()) return null;
        
        return followUpActions.stream()
            .filter(action -> action.getDueDate() != null)
            .map(FollowUpAction::getDueDate)
            .min(LocalDateTime::compareTo)
            .orElse(null);
    }

    /**
     * Create successful submission result
     */
    public static ComplianceSubmissionResult createSuccessful(String reportId, String submissionId,
                                                            String confirmationNumber, String submittedBy) {
        return ComplianceSubmissionResult.builder()
            .reportId(reportId)
            .submissionId(submissionId)
            .success(true)
            .submissionStatus(SubmissionStatus.SUBMITTED)
            .confirmationNumber(confirmationNumber)
            .submittedBy(submittedBy)
            .submittedAt(LocalDateTime.now())
            .metrics(SubmissionMetrics.builder()
                .totalProcessingTimeMs(0)
                .retryAttempts(0)
                .build())
            .build();
    }

    /**
     * Create failed submission result
     */
    public static ComplianceSubmissionResult createFailed(String reportId, String submissionId,
                                                        String errorCode, String errorMessage,
                                                        String submittedBy) {
        return ComplianceSubmissionResult.builder()
            .reportId(reportId)
            .submissionId(submissionId)
            .success(false)
            .submissionStatus(SubmissionStatus.FAILED)
            .submittedBy(submittedBy)
            .submittedAt(LocalDateTime.now())
            .submissionError(SubmissionError.builder()
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .severity(SubmissionError.ErrorSeverity.HIGH)
                .errorTimestamp(LocalDateTime.now())
                .build())
            .build();
    }
}