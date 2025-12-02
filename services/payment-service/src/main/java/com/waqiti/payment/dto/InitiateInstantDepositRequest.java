package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request to initiate an instant deposit
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiateInstantDepositRequest {
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "5000.00", message = "Amount cannot exceed 5000")
    private BigDecimal amount;
    
    @NotNull(message = "Debit card ID is required")
    private UUID debitCardId;
    
    @NotNull(message = "ACH transfer ID is required")
    private UUID achTransferId;
    
    @Size(max = 255, message = "Description cannot exceed 255 characters")
    private String description;
    
    private String ipAddress;
    private String deviceId;
    
    // For 3D Secure or additional verification
    private String verificationToken;
}