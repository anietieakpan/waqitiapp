package com.waqiti.billpayment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for bill inquiry
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillInquiryRequest {

    @NotNull(message = "Biller ID is required")
    private UUID billerId;

    @NotBlank(message = "Account number is required")
    @Size(max = 100, message = "Account number must not exceed 100 characters")
    private String accountNumber;

    @Size(max = 200, message = "Account name must not exceed 200 characters")
    private String accountName;
}
