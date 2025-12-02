package com.waqiti.billingorchestrator.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for processing a payment
 *
 * CRITICAL FINANCIAL DTO - Extensive validation
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to process a payment for an invoice")
public class ProcessPaymentRequest {

    @NotNull(message = "Invoice ID is required")
    @Schema(description = "Invoice UUID", example = "123e4567-e89b-12d3-a456-426614174000", required = true)
    private UUID invoiceId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @DecimalMax(value = "999999999.9999", message = "Amount exceeds maximum allowed")
    @Digits(integer = 15, fraction = 4, message = "Amount must have at most 15 integer digits and 4 decimal places")
    @Schema(description = "Payment amount", example = "150.50", required = true)
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be uppercase ISO 4217 code")
    @Schema(description = "Currency code (ISO 4217)", example = "USD", required = true)
    private String currency;

    @NotNull(message = "Payment method ID is required")
    @Schema(description = "Payment method UUID", example = "987fcdeb-51a2-43d7-9c8b-123456789abc", required = true)
    private UUID paymentMethodId;

    @NotBlank(message = "Payment method type is required")
    @Schema(description = "Payment method type", example = "CREDIT_CARD", required = true,
            allowableValues = {"CREDIT_CARD", "DEBIT_CARD", "ACH", "WIRE_TRANSFER", "CRYPTO", "WALLET"})
    private String paymentMethodType;

    @Schema(description = "Idempotency key for preventing duplicate payments",
            example = "pay_1234567890abcdef")
    @Size(max = 100, message = "Idempotency key cannot exceed 100 characters")
    private String idempotencyKey;

    @Schema(description = "Customer IP address for fraud detection", example = "192.168.1.100")
    private String customerIpAddress;

    @Schema(description = "Device ID for fraud detection", example = "device_abc123")
    private String deviceId;

    @Schema(description = "Additional notes", example = "Partial payment for invoice")
    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String notes;

    @Schema(description = "Save payment method for future use", example = "true")
    private Boolean savePaymentMethod;

    @Schema(description = "Request 3D Secure authentication", example = "false")
    private Boolean require3DS;

    @Schema(description = "CVV/CVC code for card payments", example = "123")
    @Pattern(regexp = "^[0-9]{3,4}$", message = "CVV must be 3 or 4 digits")
    private String cvv;

    /**
     * Validate amount is positive
     */
    @AssertTrue(message = "Payment amount must be positive")
    public boolean isValidAmount() {
        if (amount == null) {
            return true;
        }
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }
}
