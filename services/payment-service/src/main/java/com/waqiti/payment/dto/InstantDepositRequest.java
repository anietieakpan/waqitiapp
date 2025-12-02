package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request DTO for processing instant deposits
 * Contains all required information to process an immediate ACH deposit through card networks
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstantDepositRequest {
    
    @NotNull(message = "ACH transfer ID is required")
    private UUID achTransferId;
    
    @NotNull(message = "Fee acceptance is required")
    private boolean acceptedFee;
    
    @NotNull(message = "Debit card ID is required")
    private UUID debitCardId;
    
    @Size(max = 255, message = "Device ID cannot exceed 255 characters")
    private String deviceId;
    
    @Size(max = 45, message = "IP address cannot exceed 45 characters")
    private String ipAddress;
    
    @Size(max = 500, message = "User agent cannot exceed 500 characters")
    private String userAgent;
}