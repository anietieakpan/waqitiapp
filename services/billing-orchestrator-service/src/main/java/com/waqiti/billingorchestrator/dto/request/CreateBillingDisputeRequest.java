package com.waqiti.billingorchestrator.dto.request;

import com.waqiti.billingorchestrator.entity.BillingDispute.DisputeReason;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for creating billing dispute
 *
 * @author Waqiti Billing Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBillingDisputeRequest {

    @NotNull(message = "Billing cycle ID is required")
    private UUID billingCycleId;

    @NotNull(message = "Dispute reason is required")
    private DisputeReason reason;

    @NotBlank(message = "Dispute description is required")
    @Size(min = 20, max = 5000, message = "Description must be between 20 and 5000 characters")
    private String description;

    @NotNull(message = "Disputed amount is required")
    @DecimalMin(value = "0.01", message = "Disputed amount must be greater than 0")
    private BigDecimal disputedAmount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters (ISO 4217)")
    private String currency;

    @Size(max = 500, message = "Evidence URL must not exceed 500 characters")
    private String evidenceUrl;

    @Size(max = 1000, message = "Additional notes must not exceed 1000 characters")
    private String additionalNotes;
}
