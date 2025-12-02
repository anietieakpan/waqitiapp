/**
 * Create Payment Request DTO
 * Used for creating new payments
 */
package com.waqiti.payment.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentRequest {
    
    @NotNull(message = "Sender ID is required")
    private UUID senderId;
    
    @NotNull(message = "Recipient ID is required")
    private UUID recipientId;
    
    @NotBlank(message = "Recipient type is required")
    private String recipientType;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Digits(integer = 13, fraction = 2)
    private BigDecimal amount;
    
    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "[A-Z]{3}", message = "Currency must be 3-letter ISO code")
    private String currency;
    
    @Size(max = 500, message = "Description too long")
    private String description;
    
    @NotBlank(message = "Payment method is required")
    private String paymentMethod;
    
    private String paymentMethodId;
    
    private JsonNode metadata;
    
    // Reference to scheduled payment if applicable
    private UUID scheduledPaymentId;
}