package com.waqiti.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID; /**
 * Participant in a split payment request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplitPaymentParticipantRequest {
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;
}
