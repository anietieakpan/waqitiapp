package com.waqiti.legal.dto.response;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for legal contract data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegalContractResponse {

    private String contractId;
    private String contractType;
    private String contractTitle;
    private String firstPartyName;
    private String secondPartyName;
    private String contractStatus;
    private LocalDate contractStartDate;
    private LocalDate contractEndDate;
    private BigDecimal contractValue;
    private String currencyCode;
    private String jurisdiction;
    private String governingLaw;
    private String disputeResolutionMethod;
    private Integer renewalPeriodDays;
    private boolean autoRenewal;
    private LocalDate nextRenewalDate;
    private Integer renewalCount;
    private boolean requiresSignature;
    private boolean fullyExecuted;
    private LocalDateTime executedDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
