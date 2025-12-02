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
 * Request DTO for instant bill payment (without prior inquiry)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayBillInstantRequest {

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

    @NotBlank(message = "Payment method is required")
    @Size(max = 50, message = "Payment method must not exceed 50 characters")
    private String paymentMethod; // WALLET, CARD, BANK_ACCOUNT

    @Size(max = 200, message = "Account name must not exceed 200 characters")
    private String accountName;

    @Size(max = 500, message = "Notes must not exceed 500 characters")
    private String notes;

    @Size(max = 100, message = "Idempotency key must not exceed 100 characters")
    private String idempotencyKey;
}
