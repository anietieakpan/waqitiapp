package com.waqiti.legal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request DTO for creating a legal contract
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateLegalContractRequest {

    @NotBlank(message = "Contract type is required")
    private String contractType;

    @NotBlank(message = "Contract title is required")
    private String contractTitle;

    @NotBlank(message = "First party is required")
    private String firstPartyName;

    @NotBlank(message = "Second party is required")
    private String secondPartyName;

    @NotNull(message = "Contract start date is required")
    private LocalDate contractStartDate;

    private LocalDate contractEndDate;

    @DecimalMin(value = "0.0", message = "Contract value must be positive")
    private BigDecimal contractValue;

    private String currencyCode;

    private String jurisdiction;

    private String governingLaw;

    private String disputeResolutionMethod;

    private Integer renewalPeriodDays;

    private Boolean autoRenewal;

    private String terms;

    private String createdBy;
}
