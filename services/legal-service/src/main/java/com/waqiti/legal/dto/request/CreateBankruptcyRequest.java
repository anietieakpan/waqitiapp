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
 * Request DTO for creating a bankruptcy case
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBankruptcyRequest {

    @NotBlank(message = "Customer ID is required")
    private String customerId;

    @NotBlank(message = "Customer name is required")
    private String customerName;

    @NotBlank(message = "Case number is required")
    private String caseNumber;

    @NotBlank(message = "Bankruptcy chapter is required")
    private String bankruptcyChapter;

    @NotNull(message = "Filing date is required")
    private LocalDate filingDate;

    @NotBlank(message = "Court district is required")
    private String courtDistrict;

    private String trusteeName;
    private String trusteeEmail;
    private String trusteePhone;

    @NotNull(message = "Total debt amount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Total debt must be greater than zero")
    private BigDecimal totalDebtAmount;

    private String currencyCode;
}
