package com.waqiti.legal.dto.response;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for bankruptcy case data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankruptcyResponse {

    private String bankruptcyId;
    private String customerId;
    private String customerName;
    private String caseNumber;
    private String bankruptcyChapter;
    private String caseStatus;
    private LocalDate filingDate;
    private String courtDistrict;
    private String trusteeName;
    private String trusteeEmail;
    private String trusteePhone;
    private BigDecimal totalDebtAmount;
    private BigDecimal waqitiClaimAmount;
    private String claimClassification;
    private String currencyCode;
    private boolean automaticStayActive;
    private LocalDate automaticStayDate;
    private boolean accountsFrozen;
    private boolean pendingTransactionsCancelled;
    private boolean proofOfClaimFiled;
    private LocalDate proofOfClaimFilingDate;
    private LocalDate proofOfClaimBarDate;
    private boolean dischargeGranted;
    private LocalDate dischargeDate;
    private boolean dismissed;
    private LocalDate dismissalDate;
    private String dismissalReason;
    private BigDecimal expectedRecoveryPercentage;
    private BigDecimal expectedRecoveryAmount;
    private boolean creditReportingFlagged;
    private LocalDate creditReportingFlagDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
}
