package com.waqiti.wallet.dto;

import lombok.*;
import jakarta.validation.constraints.*;

/**
 * Request DTO for updating bank account status in core-banking-service.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountStatusUpdateRequest {
    
    @NotBlank(message = "Status is required")
    @Pattern(regexp = "^(ACTIVE|SUSPENDED|CLOSED|PENDING_VERIFICATION|VERIFICATION_IN_PROGRESS|VERIFICATION_FAILED)$", 
             message = "Invalid status")
    private String status;
    
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
    
    private boolean notifyUser;
    
    private String updatedBy;
}