package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request to cancel an instant deposit
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelInstantDepositRequest {
    
    @NotNull(message = "Reason is required")
    @Size(min = 5, max = 500, message = "Reason must be between 5 and 500 characters")
    private String reason;
    
    private String userComment;
}