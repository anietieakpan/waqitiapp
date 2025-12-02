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
 * Request DTO for updating an existing subscription
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update a subscription")
public class UpdateSubscriptionRequest {

    @Schema(description = "New plan ID (for upgrades/downgrades)", example = "789e0123-e89b-12d3-a456-426614174444")
    private UUID planId;

    @DecimalMin(value = "0.00", message = "Price cannot be negative")
    @Digits(integer = 15, fraction = 4)
    @Schema(description = "Updated price", example = "39.99")
    private BigDecimal price;

    @Schema(description = "Updated billing interval", example = "ANNUAL")
    private Subscription.BillingInterval billingInterval;

    @Min(value = 1, message = "Billing interval count must be at least 1")
    @Max(value = 12, message = "Billing interval count cannot exceed 12")
    @Schema(description = "Updated interval count", example = "1")
    private Integer billingIntervalCount;

    @Schema(description = "Updated payment method ID", example = "123e4567-e89b-12d3-a456-426614174555")
    private UUID paymentMethodId;

    @Schema(description = "Update auto-renewal setting", example = "false")
    private Boolean autoRenew;

    @DecimalMin(value = "0.00")
    @DecimalMax(value = "100.00")
    @Schema(description = "Updated discount percentage", example = "15.00")
    private BigDecimal discountPercentage;

    @DecimalMin(value = "0.00")
    @Schema(description = "Updated discount amount", example = "10.00")
    private BigDecimal discountAmount;

    @Schema(description = "Updated discount end date", example = "2025-06-30")
    private LocalDate discountEndDate;

    @Schema(description = "Proration behavior for changes", example = "PRORATE_IMMEDIATELY",
            allowableValues = {"PRORATE_IMMEDIATELY", "PRORATE_AT_CYCLE_END", "NO_PRORATION"})
    private String prorationBehavior;

    @Schema(description = "Effective date for changes", example = "2025-02-01")
    private LocalDate effectiveDate;

    @Schema(description = "Update notes", example = "Upgraded to premium plan")
    @Size(max = 1000, message = "Notes cannot exceed 1000 characters")
    private String notes;

    /**
     * Validate discount configuration
     */
    @AssertTrue(message = "Cannot have both percentage and fixed amount discount")
    public boolean isValidDiscount() {
        return !(discountPercentage != null && discountAmount != null);
    }

    /**
     * Validate effective date
     */
    @AssertTrue(message = "Effective date cannot be in the past")
    public boolean isValidEffectiveDate() {
        if (effectiveDate == null) {
            return true;
        }
        return !effectiveDate.isBefore(LocalDate.now());
    }
}
