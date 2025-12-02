package com.waqiti.common.compliance.mapper;

import com.waqiti.common.compliance.*;
import com.waqiti.common.compliance.dto.ComplianceDTOs;

/**
 * Mapper utility for converting between Compliance DTOs and domain models
 */
public class ComplianceMapper {

    /**
     * Convert DTO ComplianceReportRequest to domain ComplianceReportRequest
     */
    public static ComplianceReportRequest toDomain(ComplianceDTOs.ComplianceReportRequest dto) {
        if (dto == null) return null;

        return ComplianceReportRequest.builder()
            .reportType(mapReportType(dto.getReportType()))
            .title(dto.getReportType() != null ? dto.getReportType().name() : "Compliance Report")
            .requestedBy(dto.getGeneratedBy())
            .generatedBy(dto.getGeneratedBy())
            .reportPeriodStart(dto.getStartDate() != null ? dto.getStartDate().atStartOfDay() : null)
            .reportPeriodEnd(dto.getEndDate() != null ? dto.getEndDate().atStartOfDay() : null)
            .parameters(dto.getParameters())
            .regulatoryAuthority(dto.getRegulatoryAuthority())
            .build();
    }

    /**
     * Convert DTO ComplianceSubmissionRequest to domain ComplianceSubmissionRequest
     */
    public static ComplianceSubmissionRequest toDomain(ComplianceDTOs.ComplianceSubmissionRequest dto) {
        if (dto == null) return null;

        return ComplianceSubmissionRequest.builder()
            .regulatoryAuthority(dto.getRegulatoryAuthority())
            .submittedBy(dto.getSubmittedBy())
            .submissionDeadline(dto.getSubmissionDeadline())
            .submissionParameters(dto.getSubmissionParameters())
            // DTO has List<String>, domain has List<SubmissionAttachment>
            .attachments(dto.getAttachments() != null ?
                dto.getAttachments().stream()
                    .map(attachmentId -> ComplianceSubmissionRequest.SubmissionAttachment.builder()
                        .attachmentId(attachmentId)
                        .fileName(attachmentId)
                        .required(false)
                        .build())
                    .collect(java.util.stream.Collectors.toList())
                : null)
            .urgentSubmission(dto.isUrgentSubmission())
            .submissionNotes(dto.getSubmissionNotes())
            .build();
    }

    /**
     * Convert DTO SARRequest to domain SARRequest
     * Maps simple DTO fields to complex nested domain objects
     */
    public static SARRequest toDomain(ComplianceDTOs.SARRequest dto) {
        if (dto == null) return null;

        return SARRequest.builder()
            // Map transaction data - domain expects List<SuspiciousTransactionInfo>
            .transactions(dto.getTransactionId() != null ?
                java.util.List.of(SARRequest.SuspiciousTransactionInfo.builder()
                    .transactionId(dto.getTransactionId())
                    .transactionAmount(dto.getTransactionAmount())
                    .transactionDate(dto.getTransactionDate())
                    .currency("USD")
                    .build())
                : null)

            // Map contact info - domain uses this for getGeneratedBy()
            .contactInfo(dto.getGeneratedBy() != null ?
                SARRequest.ContactInfo.builder()
                    .contactName(dto.getGeneratedBy())
                    .build()
                : null)

            // Map suspicious activity - domain expects SuspiciousActivityInfo object
            .suspiciousActivity(SARRequest.SuspiciousActivityInfo.builder()
                .activityDescription(dto.getSuspiciousActivity())
                .totalSuspiciousAmount(dto.getTransactionAmount())
                .currency("USD")
                .build())

            // Simple string field for compatibility
            .suspiciousActivityDetails(dto.getSuspiciousActivity())

            // Supporting documents - convert to domain object list if needed
            // For now, keeping as simple list since domain also has List<SupportingDocumentInfo>
            .supportingDocuments(dto.getSupportingDocuments() != null ?
                dto.getSupportingDocuments().stream()
                    .map(doc -> SARRequest.SupportingDocumentInfo.builder()
                        .documentId(doc)
                        .fileName(doc)
                        .build())
                    .collect(java.util.stream.Collectors.toList())
                : null)
            .build();
    }

    /**
     * Convert DTO CTRRequest to domain CTRRequest
     * Maps simple DTO fields to complex nested domain objects
     */
    public static CTRRequest toDomain(ComplianceDTOs.CTRRequest dto) {
        if (dto == null) return null;

        return CTRRequest.builder()
            // Map contact info - domain uses this for getGeneratedBy()
            .contactInfo(dto.getGeneratedBy() != null ?
                CTRRequest.ContactInfo.builder()
                    .contactName(dto.getGeneratedBy())
                    .build()
                : null)

            // Map currency transactions - domain expects List<CurrencyTransactionInfo>
            .currencyTransactions(dto.getTransactionId() != null ?
                java.util.List.of(CTRRequest.CurrencyTransactionInfo.builder()
                    .transactionId(dto.getTransactionId())
                    .amount(dto.getTransactionAmount())
                    .currency("USD")
                    .transactionType(dto.getTransactionType())
                    .transactionTime(dto.getTransactionDate())
                    .build())
                : null)

            // Map transaction details - domain expects TransactionDetails object
            .transactionDetails(CTRRequest.TransactionDetails.builder()
                .totalTransactionAmount(dto.getTransactionAmount())
                .totalCashIn(dto.getTransactionAmount())
                .transactionDate(dto.getTransactionDate())
                .transactionType(dto.getTransactionType())
                .primaryCurrency("USD")
                .numberOfTransactions(1)
                .build())

            // Map person conducting transaction
            .personsConducting(java.util.List.of(CTRRequest.PersonConductingTransaction.builder()
                .individual(CTRRequest.IndividualInfo.builder()
                    .firstName(dto.getCustomerName() != null && dto.getCustomerName().contains(" ") ?
                        dto.getCustomerName().split(" ")[0] : dto.getCustomerName())
                    .lastName(dto.getCustomerName() != null && dto.getCustomerName().contains(" ") ?
                        dto.getCustomerName().substring(dto.getCustomerName().indexOf(" ") + 1) : "")
                    .ssn(dto.getCustomerSSN())
                    .build())
                .address(dto.getCustomerAddress() != null ?
                    CTRRequest.AddressInfo.builder()
                        .streetAddress(dto.getCustomerAddress())
                        .build()
                    : null)
                .build()))

            // Supporting documents
            .supportingDocuments(dto.getSupportingDocuments() != null ?
                dto.getSupportingDocuments().stream()
                    .map(doc -> CTRRequest.SupportingDocumentInfo.builder()
                        .documentId(doc)
                        .fileName(doc)
                        .build())
                    .collect(java.util.stream.Collectors.toList())
                : null)
            .build();
    }

    /**
     * Convert DTO ComplianceReportFilter to domain ComplianceReportFilter
     */
    public static ComplianceReportFilter toDomain(ComplianceDTOs.ComplianceReportFilter dto) {
        if (dto == null) return null;

        return ComplianceReportFilter.builder()
            // DTO has single reportType, domain has List<ComplianceReportType> reportTypes
            .reportTypes(dto.getReportType() != null ?
                java.util.List.of(mapReportType(dto.getReportType())) : null)

            // DTO has single status, domain has List<ComplianceReportStatus> statuses
            .statuses(dto.getStatus() != null ?
                java.util.List.of(ComplianceReportStatus.valueOf(dto.getStatus().name())) : null)

            // Date fields - DTO has LocalDate, domain has LocalDateTime
            .createdAfter(dto.getStartDate() != null ?
                dto.getStartDate().atStartOfDay() : null)
            .createdBefore(dto.getEndDate() != null ?
                dto.getEndDate().atTime(23, 59, 59) : null)

            // User filter - DTO has single generatedBy, domain has List<String> createdByUsers
            .createdByUsers(dto.getGeneratedBy() != null ?
                java.util.List.of(dto.getGeneratedBy()) : null)

            // Pagination
            .page(dto.getPage())
            .size(dto.getSize())
            .build();
    }

    /**
     * Map DTO ComplianceReportType to domain ComplianceReportType
     */
    private static com.waqiti.common.compliance.ComplianceReportType mapReportType(ComplianceDTOs.ComplianceReportType dtoType) {
        if (dtoType == null) return null;

        return switch (dtoType) {
            case SAR_FILING -> com.waqiti.common.compliance.ComplianceReportType.SAR_FILING;
            case CTR_FILING -> com.waqiti.common.compliance.ComplianceReportType.CTR_FILING;
            case AML_BSA_MONTHLY -> com.waqiti.common.compliance.ComplianceReportType.AML_BSA_MONTHLY;
            case AML_COMPLIANCE -> com.waqiti.common.compliance.ComplianceReportType.AML_COMPLIANCE;
            case KYC_COMPLIANCE -> com.waqiti.common.compliance.ComplianceReportType.KYC_COMPLIANCE;
            case BSA_AML_REPORT -> com.waqiti.common.compliance.ComplianceReportType.AML_BSA_MONTHLY; // Map to closest match
            case SANCTIONS_SCREENING -> com.waqiti.common.compliance.ComplianceReportType.OFAC_SCREENING;
            case TRANSACTION_MONITORING -> com.waqiti.common.compliance.ComplianceReportType.MONITORING_EXCEPTION;
            case CUSTOMER_DUE_DILIGENCE -> com.waqiti.common.compliance.ComplianceReportType.KYC_COMPLIANCE;
            case PEP_SCREENING -> com.waqiti.common.compliance.ComplianceReportType.HIGH_RISK_CUSTOMER;
            case ADVERSE_MEDIA_SCREENING -> com.waqiti.common.compliance.ComplianceReportType.HIGH_RISK_CUSTOMER;
            case RISK_ASSESSMENT -> com.waqiti.common.compliance.ComplianceReportType.RISK_ASSESSMENT;
            case REGULATORY_FILING -> com.waqiti.common.compliance.ComplianceReportType.EXAM_RESPONSE;
            case AUDIT_REPORT -> com.waqiti.common.compliance.ComplianceReportType.INTERNAL_AUDIT;
            case POLICY_REVIEW -> com.waqiti.common.compliance.ComplianceReportType.INTERNAL_AUDIT;
            case TRAINING_COMPLIANCE -> com.waqiti.common.compliance.ComplianceReportType.INTERNAL_AUDIT;
            case INCIDENT_REPORT -> com.waqiti.common.compliance.ComplianceReportType.CYBERSECURITY_INCIDENT;
            case PCI_DSS_COMPLIANCE -> com.waqiti.common.compliance.ComplianceReportType.PCI_DSS_COMPLIANCE;
            case GDPR_COMPLIANCE -> com.waqiti.common.compliance.ComplianceReportType.GDPR_COMPLIANCE;
            case SOX_COMPLIANCE -> com.waqiti.common.compliance.ComplianceReportType.SOX_COMPLIANCE;
            case HIPAA_COMPLIANCE -> com.waqiti.common.compliance.ComplianceReportType.DATA_PRIVACY;
            case FFIEC_COMPLIANCE -> com.waqiti.common.compliance.ComplianceReportType.CUSTOM;
            case DAILY_TRANSACTION_SUMMARY -> com.waqiti.common.compliance.ComplianceReportType.DAILY_TRANSACTION_SUMMARY;
            case AML_SCREENING_SUMMARY -> com.waqiti.common.compliance.ComplianceReportType.AML_SCREENING_SUMMARY;
            case FRAUD_DETECTION_SUMMARY -> com.waqiti.common.compliance.ComplianceReportType.FRAUD_DETECTION_SUMMARY;
            case AUDIT_LOG_SUMMARY -> com.waqiti.common.compliance.ComplianceReportType.AUDIT_LOG_SUMMARY;
            case CUSTOM_COMPLIANCE_REPORT -> com.waqiti.common.compliance.ComplianceReportType.CUSTOM;
            default -> com.waqiti.common.compliance.ComplianceReportType.CUSTOM;
        };
    }

    /**
     * Map domain ComplianceReportType to DTO ComplianceReportType (reverse mapping)
     */
    public static ComplianceDTOs.ComplianceReportType toDTO(com.waqiti.common.compliance.ComplianceReportType domainType) {
        if (domainType == null) return null;

        return switch (domainType) {
            case SAR -> ComplianceDTOs.ComplianceReportType.SAR_FILING;
            case CTR -> ComplianceDTOs.ComplianceReportType.CTR_FILING;
            case SAR_FILING -> ComplianceDTOs.ComplianceReportType.SAR_FILING;
            case CTR_FILING -> ComplianceDTOs.ComplianceReportType.CTR_FILING;
            case AML_BSA_MONTHLY -> ComplianceDTOs.ComplianceReportType.AML_BSA_MONTHLY;
            case AML_COMPLIANCE -> ComplianceDTOs.ComplianceReportType.AML_COMPLIANCE;
            case KYC_COMPLIANCE -> ComplianceDTOs.ComplianceReportType.KYC_COMPLIANCE;
            case OFAC_SCREENING -> ComplianceDTOs.ComplianceReportType.SANCTIONS_SCREENING;
            case MONITORING_EXCEPTION -> ComplianceDTOs.ComplianceReportType.TRANSACTION_MONITORING;
            case HIGH_RISK_CUSTOMER -> ComplianceDTOs.ComplianceReportType.PEP_SCREENING;
            case RISK_ASSESSMENT -> ComplianceDTOs.ComplianceReportType.RISK_ASSESSMENT;
            case EXAM_RESPONSE -> ComplianceDTOs.ComplianceReportType.REGULATORY_FILING;
            case INTERNAL_AUDIT -> ComplianceDTOs.ComplianceReportType.AUDIT_REPORT;
            case CYBERSECURITY_INCIDENT -> ComplianceDTOs.ComplianceReportType.INCIDENT_REPORT;
            case PCI_DSS_COMPLIANCE -> ComplianceDTOs.ComplianceReportType.PCI_DSS_COMPLIANCE;
            case GDPR_COMPLIANCE -> ComplianceDTOs.ComplianceReportType.GDPR_COMPLIANCE;
            case SOX_COMPLIANCE -> ComplianceDTOs.ComplianceReportType.SOX_COMPLIANCE;
            case DATA_PRIVACY -> ComplianceDTOs.ComplianceReportType.HIPAA_COMPLIANCE;
            case HIPAA_COMPLIANCE -> ComplianceDTOs.ComplianceReportType.HIPAA_COMPLIANCE;
            case DAILY_TRANSACTION_SUMMARY -> ComplianceDTOs.ComplianceReportType.DAILY_TRANSACTION_SUMMARY;
            case AML_SCREENING_SUMMARY -> ComplianceDTOs.ComplianceReportType.AML_SCREENING_SUMMARY;
            case FRAUD_DETECTION_SUMMARY -> ComplianceDTOs.ComplianceReportType.FRAUD_DETECTION_SUMMARY;
            case AUDIT_LOG_SUMMARY -> ComplianceDTOs.ComplianceReportType.AUDIT_LOG_SUMMARY;
            case CUSTOM -> ComplianceDTOs.ComplianceReportType.CUSTOM_COMPLIANCE_REPORT;
            default -> ComplianceDTOs.ComplianceReportType.CUSTOM_COMPLIANCE_REPORT;
        };
    }

    /**
     * Convert domain ComplianceReport to DTO ComplianceReport
     */
    public static ComplianceDTOs.ComplianceReport toDTO(com.waqiti.common.compliance.ComplianceReport domain) {
        if (domain == null) return null;

        return ComplianceDTOs.ComplianceReport.builder()
            .reportId(domain.getReportId())
            .reportType(toDTO(domain.getReportType()))
            .title(domain.getTitle())
            .generatedBy(domain.getGeneratedBy())
            .generatedAt(domain.getGeneratedAt())
            .status(domain.getStatus() != null ?
                ComplianceDTOs.ComplianceReportStatus.valueOf(domain.getStatus().name()) : null)
            .regulatoryAuthority(domain.getRegulatoryAuthority())
            .reportPeriodStart(domain.getReportPeriodStart())
            .reportPeriodEnd(domain.getReportPeriodEnd())
            .build();
    }

    /**
     * Convert domain ComplianceReportSummary to DTO ComplianceReportSummary
     */
    public static ComplianceDTOs.ComplianceReportSummary toDTO(com.waqiti.common.compliance.ComplianceReportSummary domain) {
        if (domain == null) return null;

        return ComplianceDTOs.ComplianceReportSummary.builder()
            .reports(domain.getReports() != null ?
                domain.getReports().stream()
                    .map(ComplianceMapper::toDTO)
                    .collect(java.util.stream.Collectors.toList())
                : null)
            .totalReports(domain.getTotalReports())
            .pendingReports(domain.getPendingReports())
            .completedReports(domain.getCompletedReports())
            .build();
    }

    /**
     * Convert domain ComplianceSubmissionResult to DTO ComplianceSubmissionResult
     */
    public static ComplianceDTOs.ComplianceSubmissionResult toDTO(com.waqiti.common.compliance.ComplianceSubmissionResult domain) {
        if (domain == null) return null;

        return ComplianceDTOs.ComplianceSubmissionResult.builder()
            .submissionId(domain.getSubmissionId())
            .reportId(domain.getReportId())
            .success(domain.isSuccess())
            .submittedAt(domain.getSubmittedAt())
            .confirmationNumber(domain.getConfirmationNumber())
            .regulatoryAuthority(domain.getRegulatoryAuthority())
            .statusMessage(domain.getStatusMessage())
            .build();
    }
}
