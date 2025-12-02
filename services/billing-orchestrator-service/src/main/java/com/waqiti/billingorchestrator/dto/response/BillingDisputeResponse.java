package com.waqiti.billingorchestrator.dto.response;

import com.waqiti.billingorchestrator.entity.BillingDispute.DisputeStatus;
import com.waqiti.billingorchestrator.entity.BillingDispute.DisputeReason;
import com.waqiti.billingorchestrator.entity.BillingDispute.DisputeResolution;
import com.waqiti.billingorchestrator.entity.BillingDispute.DisputePriority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for billing dispute
 *
 * @author Waqiti Billing Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingDisputeResponse {

    private UUID id;
    private UUID customerId;
    private UUID billingCycleId;
    private UUID invoiceId;
    private String invoiceNumber;
    private DisputeStatus status;
    private DisputeReason reason;
    private DisputeResolution resolutionType;
    private BigDecimal disputedAmount;
    private BigDecimal approvedRefundAmount;
    private String currency;
    private String customerDescription;
    private String merchantResponse;
    private String resolutionNotes;
    private UUID assignedTo;
    private DisputePriority priority;
    private Boolean escalated;
    private String escalationReason;
    private LocalDateTime submittedAt;
    private LocalDateTime merchantResponseDeadline;
    private LocalDateTime merchantRespondedAt;
    private LocalDateTime resolutionDate;
    private UUID resolvedBy;
    private Boolean slaBreach;
    private LocalDateTime slaDeadline;
    private Long disputeAgeHours;
    private Boolean slaApproaching;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
