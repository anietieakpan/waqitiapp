package com.waqiti.reporting.dto;

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
public class RegulatoryReportResult {

    private UUID executionId;
    private RegulatoryReportRequest.RegulatoryReportType reportType;
    private LocalDateTime generatedAt;
    private ReportDocument reportDocument;
    private RegulatorySubmissionResult submissionResult;
    private Boolean successful;
    private String errorMessage;
    private RegulatoryReportData reportData;
    private List<ValidationError> validationErrors;
    private ComplianceStatus complianceStatus;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportDocument {
        private String fileName;
        private String filePath;
        private String mimeType;
        private Long contentSize;
        private String checksum;
        private LocalDateTime createdAt;
        private Boolean encrypted;
        private String downloadUrl;
        private LocalDateTime expiresAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegulatorySubmissionResult {
        private Boolean submitted;
        private String submissionId;
        private LocalDateTime submittedAt;
        private String confirmationNumber;
        private String submissionStatus;
        private String regulatoryAuthority;
        private String acknowledgeReceipt;
        private LocalDateTime processingDeadline;
        private List<SubmissionError> errors;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegulatoryReportData {
        private RegulatoryReportRequest.RegulatoryReportType reportType;
        private LocalDateTime generatedAt;
        private List<CurrencyTransaction> ctrData;
        private List<SuspiciousActivityRecord> sarData;
        private List<ComplianceAlert> amlData;
        private List<CustomerRecord> cddData;
        private ReportMetadata metadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrencyTransaction {
        private String transactionId;
        private LocalDateTime transactionDate;
        private String customerName;
        private String customerId;
        private String accountNumber;
        private Double amount;
        private String currency;
        private String transactionType;
        private String description;
        private String reportingInstitution;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuspiciousActivityRecord {
        private String sarId;
        private LocalDateTime incidentDate;
        private String subjectName;
        private String subjectId;
        private String suspiciousActivity;
        private Double amountInvolved;
        private String currency;
        private String investigatingOfficer;
        private String narrative;
        private List<String> attachments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceAlert {
        private String alertId;
        private LocalDateTime alertDate;
        private String alertType;
        private String severity;
        private String customerId;
        private String description;
        private String status;
        private String assignedTo;
        private LocalDateTime resolvedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerRecord {
        private String customerId;
        private String customerName;
        private String customerType;
        private String riskLevel;
        private LocalDateTime kycDate;
        private LocalDateTime lastReview;
        private String reviewOutcome;
        private List<String> documentTypes;
        private Boolean pepStatus;
        private Boolean sanctionsMatch;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportMetadata {
        private String reportingPeriod;
        private String institutionName;
        private String institutionCode;
        private String contactEmail;
        private String preparerName;
        private String approverName;
        private LocalDateTime approvalDate;
        private Integer totalRecords;
        private String dataSource;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationError {
        private String fieldName;
        private String errorCode;
        private String errorMessage;
        private String severity;
        private String suggestedAction;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmissionError {
        private String errorCode;
        private String errorMessage;
        private String errorType;
        private String field;
        private String suggestedResolution;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceStatus {
        private Boolean compliant;
        private String complianceLevel;
        private List<String> exceptions;
        private List<String> recommendations;
        private Double complianceScore;
        private LocalDateTime lastAssessment;
    }
}