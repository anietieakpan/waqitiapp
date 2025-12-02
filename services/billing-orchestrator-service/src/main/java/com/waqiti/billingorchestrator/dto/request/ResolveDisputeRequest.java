package com.waqiti.billingorchestrator.dto.request;

import com.waqiti.billingorchestrator.entity.BillingDispute.DisputeResolution;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for resolving billing dispute
 *
 * @author Waqiti Billing Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveDisputeRequest {

    @NotNull(message = "Resolution type is required")
    private DisputeResolution resolutionType;

    @Size(min = 10, max = 5000, message = "Resolution notes must be between 10 and 5000 characters")
    private String resolutionNotes;

    @DecimalMin(value = "0.00", message = "Refund amount cannot be negative")
    private BigDecimal approvedRefundAmount;

    private Boolean issueCredit;

    private String creditNoteReason;
}
