package com.waqiti.billpayment.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Request to debit amount from wallet for bill payment
 * Includes comprehensive validation for production safety
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletDebitRequest {

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    private String currency;

    @NotBlank(message = "Description is required")
    private String description;

    @NotBlank(message = "Reference ID is required")
    private String referenceId;

    @NotBlank(message = "Reference type is required")
    @Builder.Default
    private String referenceType = "BILL_PAYMENT";

    private Map<String, Object> metadata;

    /**
     * Idempotency key to prevent duplicate debits
     */
    private String idempotencyKey;
}
