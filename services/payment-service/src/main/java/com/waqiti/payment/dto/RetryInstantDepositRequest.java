package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to retry a failed instant deposit
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryInstantDepositRequest {
    
    // Optional new debit card to use
    private UUID newDebitCardId;
    
    // Additional verification if required
    private String verificationToken;
    
    private boolean acceptHigherFee;
    
    private String ipAddress;
    private String deviceId;
}