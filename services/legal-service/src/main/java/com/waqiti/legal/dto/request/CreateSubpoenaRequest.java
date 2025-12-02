package com.waqiti.legal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Future;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;

/**
 * Request DTO for creating a subpoena
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSubpoenaRequest {

    @NotBlank(message = "Customer ID is required")
    private String customerId;

    @NotBlank(message = "Case number is required")
    private String caseNumber;

    @NotBlank(message = "Issuing court is required")
    private String issuingCourt;

    @NotNull(message = "Issuance date is required")
    private LocalDate issuanceDate;

    @NotNull(message = "Response deadline is required")
    @Future(message = "Response deadline must be in the future")
    private LocalDate responseDeadline;

    @NotBlank(message = "Subpoena type is required")
    private String subpoenaType;

    @NotBlank(message = "Requested records description is required")
    private String requestedRecords;

    private String servingParty;
    private String courtJurisdiction;
    private String attorneyName;
    private String attorneyContact;
    private String caseTitle;
    private boolean customerNotificationRequired;
}
