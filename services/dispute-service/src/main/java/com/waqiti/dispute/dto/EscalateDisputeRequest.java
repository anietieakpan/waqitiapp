package com.waqiti.dispute.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for escalating a dispute
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscalateDisputeRequest {

    @NotBlank(message = "Dispute ID is required")
    private String disputeId;

    @NotBlank(message = "Escalation reason is required")
    private String escalationReason;

    private String assignTo;

    @NotBlank(message = "Escalated by is required")
    private String escalatedBy;
}
