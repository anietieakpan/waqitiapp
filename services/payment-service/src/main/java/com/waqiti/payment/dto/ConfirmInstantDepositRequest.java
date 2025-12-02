package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to confirm an instant deposit
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmInstantDepositRequest {
    
    @NotNull(message = "Instant deposit ID is required")
    private UUID instantDepositId;
    
    // For additional authentication if needed
    private String authenticationCode;
    
    // User confirmation
    private boolean userAcceptsFee;
    
    private String ipAddress;
    private String deviceId;
}