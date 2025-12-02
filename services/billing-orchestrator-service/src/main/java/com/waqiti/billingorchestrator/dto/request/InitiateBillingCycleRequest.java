package com.waqiti.billingorchestrator.dto.request;

import com.waqiti.billingorchestrator.entity.BillingCycle;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for initiating a new billing cycle
 *
 * PRODUCTION-READY with comprehensive validation
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to initiate a new billing cycle")
public class InitiateBillingCycleRequest {

    @NotNull(message = "Customer ID is required")
    @Schema(description = "Customer UUID", example = "123e4567-e89b-12d3-a456-426614174000", required = true)
    private UUID customerId;

    @NotNull(message = "Account ID is required")
    @Schema(description = "Account UUID", example = "987fcdeb-51a2-43d7-9c8b-123456789abc", required = true)
    private UUID accountId;

    @NotNull(message = "Customer type is required")
    @Schema(description = "Type of customer", example = "BUSINESS", required = true)
    private BillingCycle.CustomerType customerType;

    @NotNull(message = "Start date is required")
    @Schema(description = "Billing cycle start date", example = "2025-01-01", required = true)
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    @Schema(description = "Billing cycle end date", example = "2025-01-31", required = true)
    private LocalDate endDate;

    @NotNull(message = "Billing frequency is required")
    @Schema(description = "Billing frequency", example = "MONTHLY", required = true)
    private BillingCycle.BillingFrequency billingFrequency;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters (ISO 4217)")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be uppercase ISO 4217 code (e.g., USD, EUR)")
    @Schema(description = "Currency code (ISO 4217)", example = "USD", required = true)
    private String currency;

    @Schema(description = "Payment method ID for auto-pay", example = "456e7890-e89b-12d3-a456-426614174222")
    private UUID paymentMethodId;

    @Schema(description = "Enable automatic payment", example = "true")
    private Boolean autoPayEnabled;

    @Schema(description = "Grace period in days", example = "5")
    @Min(value = 0, message = "Grace period cannot be negative")
    @Max(value = 30, message = "Grace period cannot exceed 30 days")
    private Integer gracePeriodDays;

    @Schema(description = "Additional notes", example = "Q1 2025 billing cycle")
    @Size(max = 2000, message = "Notes cannot exceed 2000 characters")
    private String notes;

    /**
     * Validate that end date is after start date
     */
    @AssertTrue(message = "End date must be after start date")
    public boolean isValidDateRange() {
        if (startDate == null || endDate == null) {
            return true; // Let @NotNull handle null checks
        }
        return endDate.isAfter(startDate);
    }

    /**
     * Validate that start date is not in the past
     */
    @AssertTrue(message = "Start date cannot be in the past")
    public boolean isValidStartDate() {
        if (startDate == null) {
            return true;
        }
        return !startDate.isBefore(LocalDate.now().minusDays(1));
    }

    /**
     * Validate auto-pay configuration
     */
    @AssertTrue(message = "Payment method ID required when auto-pay is enabled")
    public boolean isValidAutoPayConfig() {
        if (Boolean.TRUE.equals(autoPayEnabled)) {
            return paymentMethodId != null;
        }
        return true;
    }
}
