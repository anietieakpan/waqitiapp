package com.waqiti.dispute.dto;

import com.waqiti.dispute.entity.DisputeType;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDisputeRequest {

    @NotNull(message = "Transaction ID is required")
    private UUID transactionId;

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Dispute type is required")
    private DisputeType disputeType;

    @NotBlank(message = "Reason is required")
    @Size(min = 10, max = 2000, message = "Reason must be between 10 and 2000 characters")
    private String reason;

    @NotNull(message = "Disputed amount is required")
    private BigDecimal disputedAmount;

    @Size(max = 5000, message = "Description cannot exceed 5000 characters")
    private String description;

    private String merchantName;
    private String transactionDate;
    private String category;

    private String initiatorId; // added by aniix from old refactoring
    private String evidenceUrl; // added by aniix from old refactoring

}