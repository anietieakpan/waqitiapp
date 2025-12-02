package com.waqiti.billpayment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for validating a bill payment before execution
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillValidationRequest {

    @NotNull(message = "Biller ID is required")
    private UUID billerId;

    @NotBlank(message = "Account number is required")
    @Size(max = 100, message = "Account number must not exceed 100 characters")
    private String accountNumber;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    private String currency;
}
