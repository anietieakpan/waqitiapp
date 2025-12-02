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
 * Request DTO for setting up auto-pay for a bill
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetupAutoPayRequest {

    @NotNull(message = "Bill ID is required")
    private UUID billId;

    @NotBlank(message = "Payment method is required")
    @Size(max = 50, message = "Payment method must not exceed 50 characters")
    private String paymentMethod; // WALLET, CARD, BANK_ACCOUNT

    @NotBlank(message = "Amount type is required")
    @Size(max = 50, message = "Amount type must not exceed 50 characters")
    private String amountType; // FULL_BALANCE, MINIMUM_DUE, FIXED_AMOUNT

    @Positive(message = "Fixed amount must be positive")
    private BigDecimal fixedAmount; // Required if amountType = FIXED_AMOUNT

    @NotBlank(message = "Payment timing is required")
    @Size(max = 50, message = "Payment timing must not exceed 50 characters")
    private String paymentTiming; // ON_DUE_DATE, BEFORE_DUE_DATE, AFTER_DUE_DATE

    @Positive(message = "Days before/after due must be positive")
    private Integer daysBeforeDue; // Required if paymentTiming = BEFORE_DUE_DATE

    @Positive(message = "Days before/after due must be positive")
    private Integer daysAfterDue; // Required if paymentTiming = AFTER_DUE_DATE

    @Builder.Default
    private Boolean isEnabled = true;

    @Size(max = 500, message = "Notes must not exceed 500 characters")
    private String notes;
}
