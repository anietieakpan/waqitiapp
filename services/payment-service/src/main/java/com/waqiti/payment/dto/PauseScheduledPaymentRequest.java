package com.waqiti.payment.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor; /**
 * Request to pause a scheduled payment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PauseScheduledPaymentRequest {
    @Size(max = 255, message = "Reason cannot exceed 255 characters")
    private String reason;
}
