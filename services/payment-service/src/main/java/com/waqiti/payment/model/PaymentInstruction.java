package com.waqiti.payment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Validated payment instruction within a batch
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInstruction {

    @NotBlank(message = "Payment ID is required")
    @Size(max = 100, message = "Payment ID must not exceed 100 characters")
    private String paymentId;

    @NotBlank(message = "Source account ID is required")
    @Size(max = 50, message = "Source account ID must not exceed 50 characters")
    private String sourceAccountId;

    @NotBlank(message = "Destination account ID is required")
    @Size(max = 50, message = "Destination account ID must not exceed 50 characters")
    private String destinationAccountId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @DecimalMax(value = "100000.00", message = "Single payment amount cannot exceed 100,000")
    @Digits(integer = 15, fraction = 4, message = "Invalid amount format")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code")
    private String currency;

    @Pattern(regexp = "^(TRANSFER|PAYMENT|PAYOUT|REFUND)$", message = "Invalid payment type")
    private String paymentType;

    @Size(max = 100, message = "Reference must not exceed 100 characters")
    private String reference;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @Size(max = 200, message = "Beneficiary name must not exceed 200 characters")
    private String beneficiaryName;

    private Map<String, Object> metadata;
}
