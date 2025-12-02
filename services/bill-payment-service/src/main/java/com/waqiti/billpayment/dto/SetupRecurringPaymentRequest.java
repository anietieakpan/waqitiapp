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
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for setting up recurring bill payments
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetupRecurringPaymentRequest {

    @NotNull(message = "Bill ID is required")
    private UUID billId;

    @NotBlank(message = "Frequency is required")
    @Size(max = 50, message = "Frequency must not exceed 50 characters")
    private String frequency; // WEEKLY, BIWEEKLY, MONTHLY, QUARTERLY

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    private LocalDate endDate;

    @NotBlank(message = "Payment method is required")
    @Size(max = 50, message = "Payment method must not exceed 50 characters")
    private String paymentMethod; // WALLET, CARD, BANK_ACCOUNT

    @Size(max = 500, message = "Notes must not exceed 500 characters")
    private String notes;
}
