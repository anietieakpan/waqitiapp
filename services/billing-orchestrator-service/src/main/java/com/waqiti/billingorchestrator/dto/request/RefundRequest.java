package com.waqiti.billingorchestrator.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for refunding a payment
 *
 * CRITICAL FINANCIAL DTO - Refunds are irreversible
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to refund a payment")
public class RefundRequest {

    @NotNull(message = "Refund amount is required")
    @DecimalMin(value = "0.01", message = "Refund amount must be at least 0.01")
    @Digits(integer = 15, fraction = 4, message = "Amount must have at most 15 integer digits and 4 decimal places")
    @Schema(description = "Refund amount (full or partial)", example = "50.00", required = true)
    private BigDecimal amount;

    @NotBlank(message = "Refund reason is required")
    @Size(min = 10, max = 500, message = "Reason must be between 10 and 500 characters")
    @Schema(description = "Reason for refund", example = "Customer requested refund due to service issue", required = true)
    private String reason;

    @NotBlank(message = "Refund type is required")
    @Schema(description = "Type of refund", example = "FULL", required = true,
            allowableValues = {"FULL", "PARTIAL", "DISPUTE", "ERROR_CORRECTION"})
    private String refundType;

    @Schema(description = "Notify customer about refund", example = "true")
    private Boolean notifyCustomer;

    @Schema(description = "Refund initiated by (user ID or system)", example = "admin@example.com")
    private String initiatedBy;

    @Schema(description = "Internal notes (not shown to customer)", example = "Approved by manager")
    @Size(max = 1000, message = "Internal notes cannot exceed 1000 characters")
    private String internalNotes;

    @Schema(description = "Expected refund processing days", example = "5")
    @Min(value = 1, message = "Processing days must be at least 1")
    @Max(value = 30, message = "Processing days cannot exceed 30")
    private Integer expectedProcessingDays;
}
