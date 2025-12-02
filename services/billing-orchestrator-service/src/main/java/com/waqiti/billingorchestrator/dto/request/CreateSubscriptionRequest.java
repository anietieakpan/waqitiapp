package com.waqiti.billingorchestrator.dto.request;

import com.waqiti.billingorchestrator.entity.Subscription;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for creating a new subscription
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create a new subscription")
public class CreateSubscriptionRequest {

    @NotNull(message = "Customer ID is required")
    @Schema(description = "Customer UUID", example = "123e4567-e89b-12d3-a456-426614174000", required = true)
    private UUID customerId;

    @NotNull(message = "Account ID is required")
    @Schema(description = "Account UUID", example = "987fcdeb-51a2-43d7-9c8b-123456789abc", required = true)
    private UUID accountId;

    @NotNull(message = "Plan ID is required")
    @Schema(description = "Subscription plan UUID", example = "456e7890-e89b-12d3-a456-426614174222", required = true)
    private UUID planId;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.00", message = "Price cannot be negative")
    @Digits(integer = 15, fraction = 4, message = "Price must have at most 15 integer digits and 4 decimal places")
    @Schema(description = "Subscription price", example = "29.99", required = true)
    private BigDecimal price;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be uppercase ISO 4217 code")
    @Schema(description = "Currency code", example = "USD", required = true)
    private String currency;

    @NotNull(message = "Billing interval is required")
    @Schema(description = "Billing interval", example = "MONTHLY", required = true)
    private Subscription.BillingInterval billingInterval;

    @Min(value = 1, message = "Billing interval count must be at least 1")
    @Max(value = 12, message = "Billing interval count cannot exceed 12")
    @Schema(description = "Number of intervals between billings", example = "1")
    private Integer billingIntervalCount;

    @Schema(description = "Subscription start date", example = "2025-01-01")
    private LocalDate startDate;

    @Schema(description = "Trial period days", example = "14")
    @Min(value = 0, message = "Trial days cannot be negative")
    @Max(value = 365, message = "Trial period cannot exceed 365 days")
    private Integer trialDays;

    @Schema(description = "Discount percentage", example = "10.00")
    @DecimalMin(value = "0.00", message = "Discount cannot be negative")
    @DecimalMax(value = "100.00", message = "Discount cannot exceed 100%")
    private BigDecimal discountPercentage;

    @Schema(description = "Discount amount (fixed)", example = "5.00")
    @DecimalMin(value = "0.00", message = "Discount amount cannot be negative")
    private BigDecimal discountAmount;

    @Schema(description = "Discount end date", example = "2025-03-31")
    private LocalDate discountEndDate;

    @Schema(description = "Payment method ID for auto-billing", example = "123e4567-e89b-12d3-a456-426614174333")
    private UUID paymentMethodId;

    @Schema(description = "Enable auto-renewal", example = "true")
    private Boolean autoRenew;

    @Schema(description = "Subscription notes", example = "Premium plan subscription")
    @Size(max = 1000, message = "Notes cannot exceed 1000 characters")
    private String notes;

    /**
     * Validate start date
     */
    @AssertTrue(message = "Start date cannot be in the past")
    public boolean isValidStartDate() {
        if (startDate == null) {
            return true;
        }
        return !startDate.isBefore(LocalDate.now());
    }

    /**
     * Validate discount configuration
     */
    @AssertTrue(message = "Cannot have both percentage and fixed amount discount")
    public boolean isValidDiscount() {
        if (discountPercentage != null && discountAmount != null) {
            return false;
        }
        if (discountPercentage != null || discountAmount != null) {
            return discountEndDate != null;
        }
        return true;
    }
}
