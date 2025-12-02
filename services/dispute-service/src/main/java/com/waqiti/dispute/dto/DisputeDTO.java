package com.waqiti.dispute.dto;

import com.waqiti.dispute.entity.DisputeStatus;
import com.waqiti.dispute.entity.DisputeType;
import com.waqiti.dispute.entity.DisputePriority;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeDTO {

    private UUID disputeId;
    private UUID transactionId;
    private UUID userId;
    private DisputeType disputeType;
    private DisputeStatus status;
    private DisputePriority priority;
    private String reason;
    private BigDecimal disputedAmount;
    private String description;
    private String merchantName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;
    private String resolutionNotes;
    private BigDecimal refundedAmount;
    private String assignedTo;
    private LocalDateTime slaDeadline;

    private BigDecimal amount; // added by aniix - from old refactoring
    private String currency; // added by aniix - from old refactoring
}