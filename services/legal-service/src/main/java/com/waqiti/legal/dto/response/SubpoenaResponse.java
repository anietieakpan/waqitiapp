package com.waqiti.legal.dto.response;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for subpoena data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubpoenaResponse {

    private String subpoenaId;
    private String customerId;
    private String caseNumber;
    private String issuingCourt;
    private LocalDate issuanceDate;
    private LocalDate responseDeadline;
    private String subpoenaType;
    private String requestedRecords;
    private String status;
    private boolean completed;
    private boolean customerNotified;
    private LocalDateTime customerNotificationDate;
    private String servingParty;
    private String courtJurisdiction;
    private String attorneyName;
    private String attorneyContact;
    private Integer totalRecordsProduced;
    private String batesRange;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
