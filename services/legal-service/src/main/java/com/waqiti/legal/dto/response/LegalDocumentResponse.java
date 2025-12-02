package com.waqiti.legal.dto.response;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for legal document data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegalDocumentResponse {

    private String documentId;
    private String documentType;
    private String documentTitle;
    private String documentCategory;
    private String jurisdiction;
    private String documentStatus;
    private LocalDate effectiveDate;
    private LocalDate expirationDate;
    private String confidentialityLevel;
    private Integer versionNumber;
    private String currentVersionId;
    private String templateId;
    private String signedDocumentUrl;
    private boolean requiresSignature;
    private boolean fullyExecuted;
    private LocalDateTime executedDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
