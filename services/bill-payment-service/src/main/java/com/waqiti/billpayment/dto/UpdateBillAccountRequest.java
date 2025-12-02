package com.waqiti.billpayment.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating a bill account
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBillAccountRequest {

    @Size(max = 200, message = "Account name must not exceed 200 characters")
    private String accountName;

    @Size(max = 500, message = "Notes must not exceed 500 characters")
    private String notes;

    private Boolean setAsDefault;

    private Boolean isActive;
}
