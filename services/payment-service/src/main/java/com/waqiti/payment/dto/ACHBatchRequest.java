package com.waqiti.payment.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * ACH Batch creation request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ACHBatchRequest {

    @NotNull(message = "Batch type is required")
    private String batchType; // PPD, CCD, WEB, etc.

    @NotEmpty(message = "Payment IDs are required")
    private List<UUID> paymentIds;

    @NotNull(message = "Effective date is required")
    private LocalDate effectiveDate;

    private String companyName;

    private String companyId;

    private String description;
}
