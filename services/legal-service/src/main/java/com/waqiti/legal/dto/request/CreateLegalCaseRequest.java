package com.waqiti.legal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request DTO for creating a legal case
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateLegalCaseRequest {

    @NotBlank(message = "Case number is required")
    private String caseNumber;

    @NotBlank(message = "Case type is required")
    private String caseType;

    @NotBlank(message = "Case title is required")
    private String caseTitle;

    @NotNull(message = "Filing date is required")
    private LocalDate filingDate;

    @NotBlank(message = "Court is required")
    private String court;

    @NotBlank(message = "Jurisdiction is required")
    private String jurisdiction;

    private String plaintiff;
    private String defendant;
    private String assignedAttorneyId;
    private BigDecimal claimAmount;
    private String currencyCode;
    private String caseDescription;
}
