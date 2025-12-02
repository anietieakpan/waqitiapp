package com.waqiti.legal.dto.response;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for legal case data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegalCaseResponse {

    private String caseId;
    private String caseNumber;
    private String caseType;
    private String caseTitle;
    private String caseStatus;
    private LocalDate filingDate;
    private String court;
    private String jurisdiction;
    private String plaintiff;
    private String defendant;
    private String assignedAttorneyId;
    private String assignedAttorneyName;
    private BigDecimal claimAmount;
    private String currencyCode;
    private String caseDescription;
    private LocalDate hearingDate;
    private LocalDate settlementDate;
    private BigDecimal settlementAmount;
    private String outcome;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
