package com.waqiti.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal; /**
 * Request to update a participant's amount
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateParticipantAmountRequest {
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;
}
