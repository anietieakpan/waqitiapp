package com.waqiti.billpayment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for canceling a scheduled or pending payment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelPaymentRequest {

    @NotBlank(message = "Cancellation reason is required")
    @Size(max = 500, message = "Cancellation reason must not exceed 500 characters")
    private String reason;
}
