package com.waqiti.dispute.dto;

import com.waqiti.dispute.entity.DisputeStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating dispute status
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDisputeStatusRequest {

    @NotBlank(message = "Dispute ID is required")
    private String disputeId;

    @NotNull(message = "New status is required")
    private DisputeStatus newStatus;

    private String reason;

    @NotBlank(message = "Updated by is required")
    private String updatedBy;
}
