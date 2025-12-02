package com.waqiti.payment.cash.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * Request to generate a cash deposit reference
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerateReferenceRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "5.00", message = "Minimum deposit amount is $5.00")
    @DecimalMax(value = "10000.00", message = "Maximum deposit amount is $10,000.00")
    @Digits(integer = 6, fraction = 2, message = "Invalid amount format")
    private BigDecimal amount;

    @NotNull(message = "Preferred network is required")
    private CashDepositNetwork preferredNetwork;

    @Size(max = 200, message = "Description cannot exceed 200 characters")
    private String description;

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid 3-letter code")
    private String currency = "USD";

    @Email(message = "Invalid email format")
    private String notificationEmail;

    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    private String notificationPhone;

    @Builder.Default
    private boolean sendSmsInstructions = true;

    @Builder.Default
    private boolean sendEmailInstructions = true;

    @Builder.Default
    private boolean generateQrCode = true;

    @Builder.Default
    private boolean generateBarcode = true;

    private String customerReference;

    @Builder.Default
    private int expirationHours = 24;
}