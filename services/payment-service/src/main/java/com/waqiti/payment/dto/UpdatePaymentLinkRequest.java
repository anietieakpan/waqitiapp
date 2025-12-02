package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * Request DTO for updating payment links
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePaymentLinkRequest {
    
    @Size(max = 100, message = "Title cannot exceed 100 characters")
    private String title;
    
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
    
    @Size(max = 1000, message = "Custom message cannot exceed 1000 characters")
    private String customMessage;
    
    @Future(message = "Expiration date must be in the future")
    private LocalDateTime expiresAt;
    
    @Min(value = 1, message = "Max uses must be at least 1")
    @Max(value = 10000, message = "Max uses cannot exceed 10,000")
    private Integer maxUses;
    
    private Boolean requiresNote;
}