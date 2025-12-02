package com.waqiti.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeResponse {
    
    private UUID disputeId;
    private UUID transactionId;
    private String reference;
    private DisputeRequest.DisputeCategory category;
    private String reason;
    private String description;
    private BigDecimal disputedAmount;
    private DisputeStatus status;
    private List<String> supportingDocuments;
    private String contactEmail;
    private String contactPhone;
    private String assignedTo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime expectedResolutionDate;
    private String resolutionNotes;
    
    public enum DisputeStatus {
        SUBMITTED,
        UNDER_REVIEW,
        INVESTIGATING,
        PENDING_CUSTOMER_RESPONSE,
        RESOLVED,
        REJECTED,
        CANCELLED
    }
}