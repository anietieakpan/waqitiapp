package com.waqiti.legal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;

/**
 * Request DTO for creating a legal document
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateLegalDocumentRequest {

    @NotBlank(message = "Document type is required")
    private String documentType;

    @NotBlank(message = "Document title is required")
    private String documentTitle;

    private String documentCategory;

    @NotBlank(message = "Document content is required")
    private String documentContent;

    private String jurisdiction;

    @NotNull(message = "Effective date is required")
    private LocalDate effectiveDate;

    private LocalDate expirationDate;

    private String confidentialityLevel;

    private String createdBy;

    private String templateId;
}
