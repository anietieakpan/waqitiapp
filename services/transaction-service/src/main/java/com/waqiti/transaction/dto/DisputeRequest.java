package com.waqiti.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeRequest {
    
    @NotNull(message = "Reason is required")
    @Size(min = 10, max = 1000, message = "Reason must be between 10 and 1000 characters")
    private String reason;
    
    @NotNull(message = "Category is required")
    private DisputeCategory category;
    
    private BigDecimal disputedAmount;
    
    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;
    
    private List<String> supportingDocuments;
    
    private String contactEmail;
    private String contactPhone;
    
    public enum DisputeCategory {
        UNAUTHORIZED,
        DUPLICATE,
        PROCESSING_ERROR,
        SERVICE_NOT_RECEIVED,
        REFUND_NOT_PROCESSED,
        INCORRECT_AMOUNT,
        TECHNICAL_ISSUE,
        FRAUD,
        OTHER
    }
}