package com.waqiti.dispute.dto;

import com.waqiti.dispute.entity.DisputeStatus;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkUpdateRequest {

    @NotEmpty(message = "Dispute IDs list cannot be empty")
    private List<UUID> disputeIds;

    @NotNull(message = "New status is required")
    private DisputeStatus newStatus;

    private String notes;
    private String assignTo;

    private String reason; // added by aniix - from old refactoring exercise
    private String updatedBy; // added by aniix - from old refactoring exercise
}
