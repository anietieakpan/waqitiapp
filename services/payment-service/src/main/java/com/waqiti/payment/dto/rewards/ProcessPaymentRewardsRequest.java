package com.waqiti.payment.dto.rewards;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Request DTO for processing payment rewards
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessPaymentRewardsRequest implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @NotNull(message = "Payment ID is required")
    private UUID paymentId;
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    private BigDecimal amount;
    
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3-letter ISO code")
    private String currency;
    
    @NotBlank(message = "Payment method is required")
    private String paymentMethod;
    
    private String merchantCategory;
    
    private UUID merchantId;
    
    private String campaignCode;
    
    private Instant completedAt;
}