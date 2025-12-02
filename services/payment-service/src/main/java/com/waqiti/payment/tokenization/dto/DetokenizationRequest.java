package com.waqiti.payment.tokenization.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

/**
 * Detokenization request DTO
 *
 * @author Waqiti Security Team
 * @version 3.0.0
 * @since 2025-10-11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetokenizationRequest {

    @NotBlank(message = "Token is required")
    private String token;

    @NotBlank(message = "User ID is required")
    private String userId;

    private String ipAddress;

    @NotBlank(message = "Reason is required")
    private String reason; // PAYMENT_PROCESSING, CUSTOMER_SERVICE, FRAUD_INVESTIGATION, etc.
}
