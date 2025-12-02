package com.waqiti.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID; /**
 * Request to create a new payment request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentRequestRequest {
    @NotNull(message = "Recipient ID is required")
    private UUID recipientId;
    
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;
    
    @NotNull(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter code")
    private String currency;
    
    @Size(max = 255, message = "Description cannot exceed 255 characters")
    private String description;
    
    private Integer expiryHours;
}
